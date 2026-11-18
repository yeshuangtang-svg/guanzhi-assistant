import Foundation
import CryptoKit
import Security
import UIKit
import ImageIO

struct AppFailure: LocalizedError { let message: String; var errorDescription: String? { message } }
struct WebSource: Codable, Identifiable {
    var id: String { url }; let title: String; let url: String; let content: String
}
struct ChatMessage: Codable, Identifiable {
    var id = UUID(); var role: String; var text: String
    var image: String?; var sources: [WebSource] = []; var note = ""; var complete = true
}
struct Conversation: Codable, Identifiable {
    var id = UUID(); var date = Date(); var messages: [ChatMessage] = []
    var title: String { String(messages.first(where: { $0.role == "user" })?.text.prefix(24) ?? "新对话") }
}
enum LocalFiles {
    static func root() throws -> URL {
        var url = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true).appendingPathComponent("QwenVision", isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try url.setResourceValues(values)
        return url
    }
    static func imageURL(_ filename: String) throws -> URL {
        guard filename == (filename as NSString).lastPathComponent, filename.hasSuffix(".jpg") else { throw AppFailure(message: "图片路径无效") }
        return try root().appendingPathComponent(filename)
    }
    static func saveImage(_ data: Data) throws -> String {
        guard data.count <= 80 * 1024 * 1024, let source = CGImageSourceCreateWithData(data as CFData, nil),
              let thumb = CGImageSourceCreateThumbnailAtIndex(source, 0, [kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceCreateThumbnailWithTransform: true, kCGImageSourceThumbnailMaxPixelSize: 1024] as CFDictionary),
              let jpeg = UIImage(cgImage: thumb).jpegData(compressionQuality: 0.9) else { throw AppFailure(message: "无法读取图片，请选择较小的图片。") }
        let name = UUID().uuidString + ".jpg"
        try jpeg.write(to: imageURL(name), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        return name
    }
    static func saveCamera(_ image: UIImage) throws -> String {
        let scale = min(1, 1024 / max(image.size.width, image.size.height))
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let format = UIGraphicsImageRendererFormat(); format.scale = 1; format.opaque = true
        let resized = UIGraphicsImageRenderer(size: size, format: format).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        guard let data = resized.jpegData(compressionQuality: 0.9) else { throw AppFailure(message: "无法处理照片") }
        return try saveImage(data)
    }
}
enum KeyStore {
    private static var query: [String: Any] { [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "cn.shiguang.qwenvision.tavily", kSecAttrAccount as String: "search-key"] }
    static func read() throws -> String {
        var q = query; q[kSecReturnData as String] = true
        var item: CFTypeRef?; let code = SecItemCopyMatching(q as CFDictionary, &item)
        if code == errSecItemNotFound { return "" }
        guard code == errSecSuccess, let data = item as? Data, let text = String(data: data, encoding: .utf8) else { throw AppFailure(message: "无法读取钥匙串，请解锁手机后重试。") }
        return text
    }
    static func save(_ key: String) throws {
        if key.isEmpty {
            let code = SecItemDelete(query as CFDictionary)
            guard code == errSecSuccess || code == errSecItemNotFound else { throw AppFailure(message: "无法删除密钥") }; return
        }
        guard key.utf8.count <= 1024, key.utf8.allSatisfy({ $0 >= 33 && $0 <= 126 }) else { throw AppFailure(message: "API Key 格式无效") }
        let attributes: [String: Any] = [kSecValueData as String: Data(key.utf8), kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly]
        var code = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if code == errSecItemNotFound { code = SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil) }
        guard code == errSecSuccess else { throw AppFailure(message: "API Key 保存失败") }
    }
}
private final class NoRedirect: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}
enum WebSearch {
    static func safeURL(_ string: String) -> URL? {
        guard string.count <= 2048, let url = URL(string: string), ["https", "http"].contains(url.scheme?.lowercased() ?? ""), let host = url.host, !host.isEmpty, url.user == nil, url.password == nil else { return nil }; return url
    }
    static func clean(_ value: String, limit: Int) -> String {
        let chars = value.unicodeScalars.filter { !CharacterSet.controlCharacters.contains($0) || $0 == "\n" }
        return String(String(String.UnicodeScalarView(chars)).prefix(limit)).replacingOccurrences(of: "<|", with: "＜|").replacingOccurrences(of: "<__media__>", with: "＜__media__>")
    }
    static func search(_ question: String) async throws -> [WebSource] {
        guard !question.isEmpty, question.count <= 600 else { throw AppFailure(message: "联网搜索的问题请控制在 600 字以内。") }
        let key = try KeyStore.read()
        guard !key.isEmpty else { throw AppFailure(message: "请先在设置中填写 Tavily API Key。") }
        var request = URLRequest(url: URL(string: "https://api.tavily.com/search")!); request.httpMethod = "POST"; request.timeoutInterval = 25
        request.setValue("Bearer " + key, forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["query": question, "search_depth": "basic", "max_results": 3, "include_answer": false, "include_raw_content": false, "include_images": false, "auto_parameters": false])
        let config = URLSessionConfiguration.ephemeral; config.timeoutIntervalForResource = 30; config.urlCache = nil
        let session = URLSession(configuration: config, delegate: NoRedirect(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        let (stream, response) = try await session.bytes(for: request)
        guard let http = response as? HTTPURLResponse else { throw AppFailure(message: "搜索服务响应无效") }
        guard http.statusCode == 200 else {
            let message = [401: "API Key 无效，请检查设置。", 403: "搜索服务拒绝访问。", 429: "搜索请求过于频繁。", 432: "搜索额度已用完。", 433: "搜索额度已用完。"][http.statusCode] ?? "搜索未完成（HTTP \(http.statusCode)），可重试或关闭联网后回答。"
            throw AppFailure(message: message)
        }
        guard response.expectedContentLength <= 1_048_576 else { throw AppFailure(message: "搜索资料过大") }
        var data = Data(); let deadline = Date().addingTimeInterval(30)
        for try await byte in stream {
            try Task.checkCancellation()
            guard data.count < 1_048_576, Date() < deadline else { throw AppFailure(message: "搜索超时或资料过大") }
            data.append(byte)
        }
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any], let rows = json["results"] as? [[String: Any]] else { throw AppFailure(message: "搜索资料无法读取") }
        var sources: [WebSource] = []; var seen = Set<String>()
        for row in rows {
            guard let url = row["url"] as? String, safeURL(url) != nil, !seen.contains(url), let raw = row["content"] as? String else { continue }
            let content = clean(raw, limit: 320); if content.isEmpty { continue }
            sources.append(WebSource(title: clean(row["title"] as? String ?? "网页资料", limit: 80), url: url, content: content)); seen.insert(url)
            if sources.count == 3 { break }
        }
        guard !sources.isEmpty else { throw AppFailure(message: "没有找到可用资料，请修改问题或关闭联网搜索。") }
        return sources
    }
}
enum ModelAssets {
    struct Entry: Decodable { let name: String; let size: Int64; let sha256: String }
    struct Manifest: Decodable { let files: [Entry] }
    static func verify() throws -> (String, String) {
        guard let folder = Bundle.main.resourceURL?.appendingPathComponent("Models") else { throw AppFailure(message: "缺少内置模型") }
        let data = try Data(contentsOf: folder.appendingPathComponent("manifest.json"))
        let files = try JSONDecoder().decode(Manifest.self, from: data).files
        let expected: Set<String> = ["model-allint-w4w8.gguf", "vision-allint-q8.gguf"]
        guard Set(files.map(\.name)) == expected, files.count == 2 else { throw AppFailure(message: "轻量版模型清单不匹配") }
        let fingerprint = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined() + (Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "")
        let stamp = try LocalFiles.root().appendingPathComponent("models.verified")
        let verified = (try? String(contentsOf: stamp, encoding: .utf8)) == fingerprint
        for entry in files {
            let url = folder.appendingPathComponent(entry.name)
            let attributes = try FileManager.default.attributesOfItem(atPath: url.path)
            guard (attributes[.size] as? NSNumber)?.int64Value == entry.size else { throw AppFailure(message: "模型文件不完整，请重新安装。") }
            if !verified {
                let file = try FileHandle(forReadingFrom: url); defer { try? file.close() }; var hash = SHA256()
                while let chunk = try file.read(upToCount: 4 * 1024 * 1024), !chunk.isEmpty { hash.update(data: chunk) }
                guard hash.finalize().map({ String(format: "%02x", $0) }).joined() == entry.sha256 else { throw AppFailure(message: "模型校验失败，请重新安装。") }
            }
        }
        try fingerprint.write(to: stamp, atomically: true, encoding: .utf8)
        return (folder.appendingPathComponent("model-allint-w4w8.gguf").path, folder.appendingPathComponent("vision-allint-q8.gguf").path)
    }
}

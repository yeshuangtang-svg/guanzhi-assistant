import SwiftUI
import PhotosUI
import UIKit

@main struct QwenVisionApp: App {
    @StateObject private var store = AssistantStore()
    var body: some Scene { WindowGroup { AssistantView().environmentObject(store) } }
}
struct AssistantView: View {
    @EnvironmentObject private var store: AssistantStore
    @Environment(\.scenePhase) private var scenePhase
    @State private var settings = false
    @State private var history = false
    @State private var camera = false
    @State private var photo: PhotosPickerItem?
    @State private var importing = false
    private let purple = Color(red: 0.39, green: 0.25, blue: 0.85)
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Button { settings = true } label: {
                    HStack(spacing: 6) { if store.busy { ProgressView().scaleEffect(0.7) }; Text(store.status).font(.caption); Image(systemName: "chevron.down").font(.caption2) }.foregroundStyle(purple).padding(.vertical, 8)
                }.disabled(store.busy)
                ScrollViewReader { reader in
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 20) {
                            if store.current.messages.isEmpty {
                                VStack(alignment: .leading, spacing: 18) {
                                    Image(systemName: "viewfinder").font(.system(size: 48, weight: .light)).foregroundStyle(purple)
                                    Text("看见问题，一起想清楚").font(.title2.bold())
                                    Text("拍照、选图，或直接提问。\n你的量化模型在 iPhone 本地回答。").foregroundStyle(.secondary)
                                    ForEach(["解释这张图中的关键信息", "帮我一步步分析这道题", "这张图里有哪些不确定的细节？"], id: \.self) { prompt in
                                        Button { store.draft = prompt } label: { HStack { Text(prompt).font(.subheadline); Spacer(); Image(systemName: "arrow.up.left") }.padding().background(purple.opacity(0.06), in: RoundedRectangle(cornerRadius: 16)) }
                                    }
                                }.padding(.top, 32)
                            }
                            ForEach(store.current.messages) { message in messageView(message).id(message.id) }
                            Color.clear.frame(height: 1).id("bottom")
                        }.padding(20)
                    }
                    .onChange(of: store.current.messages.last?.text) { _, _ in reader.scrollTo("bottom", anchor: .bottom) }
                    .onChange(of: store.current.messages.count) { _, _ in reader.scrollTo("bottom", anchor: .bottom) }
                }
                composer
            }
            .background(Color(uiColor: .systemBackground))
            .navigationTitle("千问·视界").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button { history = true } label: { Image(systemName: "line.3.horizontal") }.disabled(store.busy) }
                ToolbarItem(placement: .topBarTrailing) { Button { store.newChat() } label: { Image(systemName: "square.and.pencil") }.disabled(store.busy) }
            }
            .sheet(isPresented: $settings) { SettingsView().environmentObject(store) }
            .sheet(isPresented: $history) { NavigationStack { List(store.chats) { chat in Button { store.select(chat.id); history = false } label: { VStack(alignment: .leading) { Text(chat.title); Text(chat.date.formatted()).font(.caption).foregroundStyle(.secondary) } } }.navigationTitle("历史对话").toolbar { Button("完成") { history = false } } } }
            .sheet(isPresented: $camera) { CameraView { image in camera = false; if let image = image { do { store.attachment = try LocalFiles.saveCamera(image) } catch { store.error = error.localizedDescription } } } }
            .alert("提示", isPresented: Binding(get: { !store.error.isEmpty }, set: { if !$0 { store.error = "" } })) { Button("知道了") { store.error = "" } } message: { Text(store.error) }
            .task { store.load() }
            .onChange(of: scenePhase) { _, phase in store.scene(phase) }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.didReceiveMemoryWarningNotification)) { _ in store.memoryWarning() }
            .onChange(of: photo) { _, item in
                guard let item = item else { return }; importing = true
                Task {
                    defer { importing = false; photo = nil }
                    do { guard let data = try await item.loadTransferable(type: Data.self) else { throw AppFailure(message: "照片读取失败") }; store.attachment = try LocalFiles.saveImage(data) }
                    catch { store.error = "无法导入图片，请重试。" }
                }
            }
        }.tint(purple)
    }
    @ViewBuilder private func localImage(_ name: String) -> some View {
        if let url = try? LocalFiles.imageURL(name), let image = UIImage(contentsOfFile: url.path) { Image(uiImage: image).resizable().scaledToFit().frame(maxHeight: 220).clipShape(RoundedRectangle(cornerRadius: 14)) }
    }
    private func messageView(_ message: ChatMessage) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(message.role == "user" ? "你" : "千问·视界").font(.caption.bold()).foregroundStyle(.secondary)
            if let image = message.image { localImage(image) }
            Text(message.text.isEmpty ? "正在准备回答…" : message.text).textSelection(.enabled).frame(maxWidth: .infinity, alignment: .leading)
            if !message.sources.isEmpty {
                DisclosureGroup("网页来源 · \(message.sources.count)") {
                    ForEach(message.sources) { source in VStack(alignment: .leading, spacing: 4) {
                        if let url = WebSearch.safeURL(source.url) { Link(source.title, destination: url) }
                        Text(source.content).font(.caption).foregroundStyle(.secondary)
                    }.padding(.vertical, 4) }
                }.font(.footnote)
            }
            if !message.note.isEmpty { Text(message.note).font(.caption2).foregroundStyle(.secondary) }
            if message.role == "assistant", message.complete {
                HStack { Button { UIPasteboard.general.string = message.text } label: { Label("复制", systemImage: "doc.on.doc") }
                    if message.id == store.current.messages.last?.id { Button { store.retry() } label: { Label("重试", systemImage: "arrow.clockwise") }.disabled(store.busy) }
                }.font(.caption)
            }
        }.padding(16).background(message.role == "user" ? purple.opacity(0.07) : Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 18))
    }
    private var composer: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let name = store.attachment { HStack { localImage(name).frame(width: 64, height: 64); Button("移除图片", role: .destructive) { store.attachment = nil } }.disabled(store.busy) }
            HStack(alignment: .bottom) {
                TextField("有什么想问的？", text: $store.draft, axis: .vertical).lineLimit(1...5).disabled(store.busy)
                if store.busy { if store.ready { Button { store.stop() } label: { Image(systemName: "stop.circle.fill").font(.title2) } } else { ProgressView() } }
                else { Button { store.send() } label: { Image(systemName: "arrow.up.circle.fill").font(.title2) }.disabled(!store.ready || importing || store.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) }
            }
            HStack(spacing: 18) {
                PhotosPicker(selection: $photo, matching: .images) { Image(systemName: "photo") }.disabled(store.busy || importing)
                Button { camera = true } label: { Image(systemName: "camera") }.disabled(store.busy || !UIImagePickerController.isSourceTypeAvailable(.camera))
                Toggle(isOn: $store.webEnabled) { Label("联网搜索", systemImage: "globe").font(.caption) }.toggleStyle(.button).disabled(store.busy)
                Spacer()
                if !store.ready && !store.busy { Button("加载模型") { store.load() }.font(.caption) }
            }.font(.body)
        }.padding(16).background(.regularMaterial)
    }
}
struct SettingsView: View {
    @EnvironmentObject private var store: AssistantStore
    @Environment(\.dismiss) private var dismiss
    @State private var key = ""
    @State private var notice = ""
    var body: some View {
        NavigationStack {
            Form {
                Section("速度档位") {
                    Picker("处理器", selection: Binding(get: { store.gpu }, set: { store.configure(gpu: $0, tokens: store.imageTokens) })) { Text("轻快 · Metal GPU").tag(true); Text("兼容 · CPU").tag(false) }
                    Text("两档使用同一套量化模型。GPU 初始化失败会尝试 CPU；实际速度以首字计时为准。").font(.caption).foregroundStyle(.secondary)
                    Text("实际后端：\(store.backend)").font(.caption)
                }.disabled(store.busy)
                Section("看图精度") {
                    Picker("图片预算", selection: Binding(get: { store.imageTokens }, set: { store.configure(gpu: store.gpu, tokens: $0) })) { Text("快速 · 256").tag(256); Text("均衡 · 384").tag(384); Text("精细 · 768").tag(768) }
                    Text("快速档减少图像处理量。小字和复杂图表可选精细；上下文为 2048 tokens，最多使用最近一张图片。").font(.caption).foregroundStyle(.secondary)
                }.disabled(store.busy)
                Section("联网搜索 · Tavily") {
                    SecureField("在手机填写 API Key", text: $key).textInputAutocapitalization(.never).autocorrectionDisabled()
                    Button("保存密钥") { do { try KeyStore.save(key.trimmingCharacters(in: .whitespacesAndNewlines)); notice = "已保存到此设备钥匙串" } catch { notice = error.localizedDescription } }
                    if !notice.isEmpty { Text(notice).font(.caption) }
                    Text("手动开启后，仅将本次问题发给搜索服务，网页摘要交由本地模型回答。图片、历史与模型权重不会上传。清空并保存可删除密钥。").font(.caption).foregroundStyle(.secondary)
                }
                Section("关于") {
                    Text("千问·视界 · iOS 轻量版 1.0")
                    Text("面向 iPhone 16 Pro / A18 Pro。只内置你的 W4/W8 移动适配模型：Q4/Q8 矩阵、一个 BF16 矩阵及浮点小参数。使用 Metal GPU 或 CPU，未使用 Neural Engine。模型可能答错，请核对关键结论。").font(.caption)
                    Text("基于 Qwen3.5 和 llama.cpp 的个人项目，非千问官方 App。进入后台会停止回答并释放模型；卸载会删除本机会话。").font(.caption)
                }
            }.navigationTitle("设置").toolbar { Button("完成") { dismiss() } }
                .task { do { key = try KeyStore.read() } catch { notice = error.localizedDescription } }
        }
    }
}
struct CameraView: UIViewControllerRepresentable {
    let completion: (UIImage?) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(completion) }
    func makeUIViewController(context: Context) -> UIImagePickerController { let picker = UIImagePickerController(); picker.sourceType = .camera; picker.cameraCaptureMode = .photo; picker.delegate = context.coordinator; return picker }
    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}
    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let completion: (UIImage?) -> Void
        init(_ completion: @escaping (UIImage?) -> Void) { self.completion = completion }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { completion(nil) }
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) { completion(info[.originalImage] as? UIImage) }
    }
}

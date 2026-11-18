import SwiftUI
import UIKit

@MainActor final class AssistantStore: ObservableObject {
    @Published var chats: [Conversation] = []
    @Published var selected = UUID()
    @Published var draft = ""
    @Published var attachment: String?
    @Published var busy = false
    @Published var ready = false
    @Published var status = "正在准备模型"
    @Published var backend = "未加载"
    @Published var error = ""
    @Published var webEnabled = false
    @Published var gpu = true
    @Published var imageTokens = 256
    private let worker = DispatchQueue(label: "qwen.inference", qos: .userInitiated)
    private var searchTask: Task<Void, Never>?
    private var run = UUID()
    private var started = Date()
    private var firstToken = false
    private var phaseTimer: Timer?
    private var writable = true
    private var stopped = false
    private var active = true
    private var loadRevision = UUID()
    private var needsResume = false
    var current: Conversation { chats.first(where: { $0.id == selected }) ?? Conversation() }

    init() {
        let prefs = UserDefaults.standard
        gpu = prefs.object(forKey: "gpu") == nil ? true : prefs.bool(forKey: "gpu")
        imageTokens = [256,384,768].contains(prefs.integer(forKey: "imageTokens")) ? prefs.integer(forKey: "imageTokens") : 256
        if prefs.bool(forKey: "engineInFlight") { gpu = false; prefs.set(false, forKey: "gpu"); status = "上次推理中断，先使用 CPU" }
        do {
            let url = try LocalFiles.root().appendingPathComponent("chats.json")
            if FileManager.default.fileExists(atPath: url.path) {
                chats = try JSONDecoder().decode([Conversation].self, from: Data(contentsOf: url))
                for i in chats.indices { for j in chats[i].messages.indices where !chats[i].messages[j].complete { chats[i].messages[j].complete = true; chats[i].messages[j].note += " · 上次回答中断" } }
            }
        } catch { writable = false; self.error = "历史读取失败，原文件已保留，本次对话暂不保存。" }
        if chats.isEmpty { chats = [Conversation()] }; selected = chats[0].id
    }
    func save() {
        guard writable else { return }
        do { try JSONEncoder().encode(chats).write(to: LocalFiles.root().appendingPathComponent("chats.json"), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication]) }
        catch { self.error = "历史记录保存失败。" }
    }
    func newChat() {
        guard !busy else { return }; let chat = Conversation(); chats.insert(chat, at: 0); selected = chat.id; draft = ""; attachment = nil; save()
    }
    func select(_ id: UUID) { guard !busy else { return }; selected = id; attachment = nil; draft = "" }
    func load() {
        guard !busy, !ready, active else { return }
        busy = true; status = "正在校验并加载量化模型"; let useGPU = gpu; let budget = imageTokens
        needsResume = false; let revision = UUID(); loadRevision = revision
        UserDefaults.standard.set(useGPU, forKey: "engineInFlight")
        worker.async {
            var error = ""; var actual = "未加载"
            do { let (lm, mm) = try ModelAssets.verify(); error = QVEngine.load(language: lm, vision: mm, gpu: useGPU, imageTokens: budget); if error.isEmpty { actual = QVEngine.backend() } }
            catch { error = error.localizedDescription }
            let result = error; let selectedBackend = actual
            DispatchQueue.main.async {
                UserDefaults.standard.set(false, forKey: "engineInFlight")
                guard revision == self.loadRevision else { self.busy = false; if self.needsResume && self.active { self.load() }; return }
                self.busy = false; self.ready = result.isEmpty; self.backend = selectedBackend
                self.status = result.isEmpty ? "W4/W8 · \(selectedBackend)" : "模型加载失败"
                if !result.isEmpty { self.error = result }
                if !self.active { self.releaseModel() }
            }
        }
    }
    func configure(gpu: Bool, tokens: Int) {
        guard !busy else { return }
        self.gpu = gpu; self.imageTokens = tokens
        UserDefaults.standard.set(gpu, forKey: "gpu"); UserDefaults.standard.set(tokens, forKey: "imageTokens")
        ready = false; load()
    }
    func retry() {
        guard !busy, let i = chats.firstIndex(where: { $0.id == selected }), let last = chats[i].messages.last else { return }
        if last.role == "assistant" { chats[i].messages.removeLast() }
        guard let user = chats[i].messages.last, user.role == "user" else { return }
        chats[i].messages.removeLast(); draft = user.text; attachment = user.image; send()
    }
    func send() {
        let question = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !busy, ready, !question.isEmpty, let index = chats.firstIndex(where: { $0.id == selected }) else { return }
        guard question.count <= 8000 else { error = "问题过长，请缩短文字。"; return }
        let user = ChatMessage(role: "user", text: question, image: attachment)
        chats[index].messages.append(user)
        let answer = ChatMessage(role: "assistant", text: "", note: webEnabled ? "联网搜索中" : "本地回答", complete: false)
        chats[index].messages.append(answer); chats[index].date = Date(); save()
        let history = Array(chats[index].messages.dropLast()); let chatID = selected; let messageID = answer.id
        draft = ""; attachment = nil; busy = true; stopped = false; firstToken = false; started = Date(); run = UUID()
        let token = run; QVEngine.resetCancel()
        status = webEnabled ? "正在检索网页资料" : "正在处理输入"
        let useWeb = webEnabled
        searchTask = Task {
            do {
                let sources = useWeb ? try await WebSearch.search(question) : []
                try Task.checkCancellation()
                guard self.run == token, !self.stopped else { return }
                self.update(chatID, messageID) { $0.sources = sources; $0.note = useWeb ? "检索时间：\(Date().formatted())" : "本地回答" }
                self.infer(history, sources: sources, chatID: chatID, messageID: messageID, token: token)
            } catch {
                self.finish(chatID, messageID, token: token, error: self.stopped ? "已停止" : "搜索未完成：\((error as? AppFailure)?.message ?? "连接失败，请检查网络后重试。")", stats: "")
            }
        }
    }
    private func update(_ chat: UUID, _ message: UUID, _ mutate: (inout ChatMessage) -> Void) {
        guard let i = chats.firstIndex(where: { $0.id == chat }), let j = chats[i].messages.firstIndex(where: { $0.id == message }) else { return }; mutate(&chats[i].messages[j])
    }
    private func infer(_ history: [ChatMessage], sources: [WebSource], chatID: UUID, messageID: UUID, token: UUID) {
        status = "正在处理输入"; UserDefaults.standard.set(gpu, forKey: "engineInFlight")
        phaseTimer?.invalidate()
        phaseTimer = Timer.scheduledTimer(withTimeInterval: 0.3, repeats: true) { [weak self] _ in Task { @MainActor in
            guard let self = self, self.busy else { return }; let phase = QVEngine.status(); if !phase.isEmpty { self.status = phase }
        } }
        let web = sources.enumerated().map { "[\($0.offset+1)] \($0.element.title)\n\($0.element.content)\n" }.joined(separator: "\n")
        worker.async {
            let roles = history.map(\.role); let texts = history.map(\.text)
            var paths: [String] = []
            do { paths = try history.map { message in if let name = message.image { return try LocalFiles.imageURL(name).path }; return "" } }
            catch { DispatchQueue.main.async { self.finish(chatID, messageID, token: token, error: "无法读取历史图片", stats: "") }; return }
            var bytes = Data(); var last = Date.distantPast
            let failure = QVEngine.generate(roles: roles, texts: texts, images: paths, web: web, maxTokens: 384) { data in
                bytes.append(data)
                // Preserve UTF-8 token boundaries; publish at most 20 updates per second.
                if Date().timeIntervalSince(last) >= 0.05, let text = String(data: bytes, encoding: .utf8) {
                    last = Date(); DispatchQueue.main.async { self.receive(text, chatID, messageID, token: token) }
                }
            }
            let text = String(decoding: bytes, as: UTF8.self); let statistics = QVEngine.stats()
            DispatchQueue.main.async {
                if !text.isEmpty { self.receive(text, chatID, messageID, token: token) }
                self.finish(chatID, messageID, token: token, error: failure, stats: statistics)
            }
        }
    }
    private func receive(_ text: String, _ chat: UUID, _ message: UUID, token: UUID) {
        guard token == run else { return }
        if !firstToken { firstToken = true; let seconds = Date().timeIntervalSince(started); update(chat, message) { $0.note += String(format: " · 首字 %.1fs", seconds) } }
        update(chat, message) { $0.text = text }
    }
    private func finish(_ chat: UUID, _ message: UUID, token: UUID, error: String, stats: String) {
        guard token == run else { return }
        phaseTimer?.invalidate(); phaseTimer = nil; searchTask = nil
        UserDefaults.standard.set(false, forKey: "engineInFlight")
        update(chat, message) {
            $0.complete = true
            if !error.isEmpty { $0.note += " · " + error }
            if stopped { $0.note += " · 已停止" }
            if !stats.isEmpty { $0.note += "\n" + stats }
            if $0.text.isEmpty { $0.text = stopped ? "回答已停止。" : (error.isEmpty ? "模型未生成内容，可重新提问。" : error) }
        }
        busy = false; status = ready ? "W4/W8 · \(backend)" : "模型已暂停"; save()
        if needsResume && active { load() }
    }
    func stop() { stopped = true; QVEngine.cancel(); searchTask?.cancel(); status = "正在停止" }
    func scene(_ phase: ScenePhase) {
        active = phase == .active
        if phase == .background { if busy { stop() }; releaseModel() }
        if phase == .active { needsResume = true; load() }
    }
    func memoryWarning() {
        if busy { stop() }; releaseModel(); error = "系统内存紧张，已释放模型。可切换 CPU 或降低看图精度后重新加载。"
    }
    private func releaseModel() {
        ready = false; loadRevision = UUID(); worker.async { QVEngine.unload() }
    }
}

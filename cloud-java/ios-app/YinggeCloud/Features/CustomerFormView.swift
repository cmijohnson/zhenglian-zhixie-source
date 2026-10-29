import SwiftUI

struct CustomerFormView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let customer: Customer?
    @State private var draft: CustomerWriteRequest
    init(customer: Customer?) { self.customer = customer; _draft = State(initialValue: CustomerWriteRequest(customer: customer)) }
    var body: some View {
        Form {
            Section("基础信息") {
                TextField("姓名 *", text: $draft.fullName)
                TextField("昵称", text: optional($draft.nickname))
                TextField("手机号", text: optional($draft.phone)).keyboardType(.phonePad)
                TextField("微信", text: optional($draft.wechatId)).textInputAutocapitalization(.never)
                TextField("QQ", text: optional($draft.qqId)).keyboardType(.numberPad)
            }
            Section("教育画像") {
                TextField("大学", text: optional($draft.school)); TextField("专业", text: optional($draft.major))
                TextField("年级", text: optional($draft.grade)); TextField("高中学校", text: optional($draft.highSchool))
                TextField("生源地", text: optional($draft.sourceLocation)); TextField("所在城市", text: optional($draft.city))
            }
            Section("跟进判断") {
                Picker("当前状态", selection: $draft.currentStatus) { ForEach(["初次接触", "跟进中", "待回访", "暂停跟进"], id: \.self) { Text($0) } }
                Picker("意向", selection: $draft.intentionLevel) { ForEach(["低", "中", "高"], id: \.self) { Text($0) } }
                Picker("紧急程度", selection: $draft.urgencyLevel) { ForEach(["低", "中", "高"], id: \.self) { Text($0) } }
                TextField("需求摘要", text: optional($draft.demandSummary), axis: .vertical).lineLimit(3...8)
                TextField("内部备注", text: optional($draft.ownerNotes), axis: .vertical).lineLimit(3...8)
                DatePicker("下次跟进", selection: Binding(get: { draft.nextFollowUpAt ?? Date() }, set: { draft.nextFollowUpAt = $0 }))
            }
        }.navigationTitle(customer == nil ? "新建客户" : "编辑客户").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("保存") { Task { if await model.saveCustomer(draft, id: customer?.id) != nil { dismiss() } } }.disabled(draft.fullName.trimmingCharacters(in: .whitespaces).isEmpty || model.busy) }
            }
    }
    private func optional(_ binding: Binding<String?>) -> Binding<String> {
        Binding(get: { binding.wrappedValue ?? "" }, set: { binding.wrappedValue = $0.isEmpty ? nil : $0 })
    }
}


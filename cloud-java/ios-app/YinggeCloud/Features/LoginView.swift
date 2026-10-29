import SwiftUI

struct LoginView: View {
    @EnvironmentObject private var model: AppModel
    @State private var username = ""
    @State private var password = ""
    @State private var showServer = false
    @State private var showRegister = false
    var body: some View {
        ZStack {
            YinggeTheme.mist.ignoresSafeArea()
            Circle().fill(YinggeTheme.blue.opacity(0.12)).frame(width: 360).blur(radius: 4).offset(x: 150, y: -310)
            ScrollView {
                VStack(alignment: .leading, spacing: 26) {
                    Image("BrandLogo").resizable().scaledToFit().frame(maxWidth: 260).accessibilityLabel("英格客户云")
                    VStack(alignment: .leading, spacing: 8) {
                        SectionEyebrow(text: "Welcome back")
                        Text("把每一次跟进，\n变成可行动的增长信号").font(.system(size: 32, weight: .bold, design: .rounded)).foregroundStyle(YinggeTheme.navy)
                    }
                    VStack(spacing: 14) {
                        TextField("账号", text: $username).textContentType(.username).textInputAutocapitalization(.never)
                            .padding(15).background(YinggeTheme.mist, in: RoundedRectangle(cornerRadius: 14))
                        SecureField("密码", text: $password).textContentType(.password).padding(15)
                            .background(YinggeTheme.mist, in: RoundedRectangle(cornerRadius: 14))
                        Button {
                            Task { await model.login(username: username, password: password) }
                        } label: {
                            Group { if model.busy { ProgressView().tint(.white) } else { Text("登录").fontWeight(.semibold) } }
                                .frame(maxWidth: .infinity, minHeight: 52)
                        }.buttonStyle(.plain).foregroundStyle(.white).background(YinggeTheme.gradient, in: RoundedRectangle(cornerRadius: 16))
                            .disabled(username.isEmpty || password.isEmpty || model.busy)
                        Button("服务器设置") { showServer = true }.font(.footnote).foregroundStyle(.secondary)
                        Button("使用邀请码注册") { showRegister = true }.font(.footnote.bold())
                    }.yinggeCard()
                }.padding(24).padding(.top, 44)
            }
        }.sheet(isPresented: $showServer) { ServerSettingsView() }
            .sheet(isPresented: $showRegister) { NavigationStack { RegistrationView() } }
    }
}

struct RegistrationView: View {
    @EnvironmentObject private var model: AppModel; @Environment(\.dismiss) private var dismiss
    @State private var code = ""; @State private var teams: [RegistrationTeam] = []; @State private var teamID = ""
    @State private var username = ""; @State private var realName = ""; @State private var password = ""
    var body: some View {
        Form {
            Section("邀请码") {
                TextField("输入邀请码", text: $code).textInputAutocapitalization(.never)
                Button("验证并加载团队") { Task { teams = await model.registrationOptions(code: code); teamID = teams.first?.id ?? "" } }.disabled(code.isEmpty)
            }
            if !teams.isEmpty {
                Section("加入团队") { Picker("团队", selection: $teamID) { ForEach(teams) { Text($0.name).tag($0.id) } } }
                Section("真实身份") {
                    TextField("账号", text: $username).textInputAutocapitalization(.never)
                    TextField("真实姓名", text: $realName); SecureField("密码", text: $password)
                }
            }
        }.navigationTitle("员工注册").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("注册并登录") { Task { if await model.register(username: username, realName: realName, password: password, code: code, teamID: teamID) { dismiss() } } }
                        .disabled(teamID.isEmpty || username.isEmpty || realName.isEmpty || password.isEmpty || model.busy)
                }
            }
    }
}

struct PasswordChangeView: View {
    @EnvironmentObject private var model: AppModel
    @State private var current = ""; @State private var new = ""; @State private var confirm = ""
    var body: some View {
        NavigationStack {
            Form {
                Section { Text("管理员要求你在继续使用前修改密码。修改成功后所有会话会撤销，请用新密码重新登录。") }
                Section("修改密码") { SecureField("当前密码", text: $current); SecureField("新密码", text: $new); SecureField("确认新密码", text: $confirm) }
                Button("修改并重新登录") { Task { _ = await model.changePassword(current: current, new: new) } }
                    .disabled(current.isEmpty || new.isEmpty || new != confirm || model.busy)
            }.navigationTitle("必须修改密码")
        }
    }
}

struct ServerSettingsView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var value = ""
    var body: some View {
        NavigationStack {
            Form {
                Section("客户云地址") {
                    TextField("http://192.0.2.10", text: $value).textInputAutocapitalization(.never).keyboardType(.URL)
                    Text("支持 HTTPS；若使用 HTTP，登录凭据和业务数据不会获得传输加密保护。")
                        .font(.footnote).foregroundStyle(.orange)
                }
            }.navigationTitle("服务器设置").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("保存") { Task { if await model.applyServer(value) { dismiss() } } }.disabled(value.isEmpty || model.busy)
                    }
                }.onAppear { value = model.serverURL }
        }.presentationDetents([.medium])
    }
}

// AI-assisted web conversion output — ticket 47 fixture.
// Source: login-form.html. Mapping decisions and downgrades: login-form.conversion-report.json.
// Executed by ui-authoring-docs-proof.tsx (TypeScriptUiAuthoringDocsTest).
import { UI } from 'nekojs/jsx-runtime';

// Web form state (username input, password input, error message, submit loading)
// becomes explicit signals — there are no DOM form semantics here.
export const username = UI.createSignal('');
export const password = UI.createSignal('');
export const error = UI.createSignal('');
export const submitting = UI.createSignal(false);
export const lastSubmitted = UI.createSignal('');

function submit() {
  if (username.get() === '' || password.get() === '') {
    error.set('请输入用户名和密码');
    return;
  }
  error.set('');
  submitting.set(true);
  lastSubmitted.set(username.get());
}

function cancel() {
  username.set('');
  password.set('');
  error.set('');
  submitting.set(false);
}

function UsernameField() {
  return (
    <column id="login-username-group" gap={2}>
      <label id="login-username-label" fontSize={9}>{'用户名'}</label>
      <input id="login-username" value={username.get()} placeholder="请输入用户名" maxLength={32}
             height={12} disabled={submitting.get()}
             onChange={event => username.set(event.value == null ? '' : event.value)} />
    </column>
  );
}

function PasswordField() {
  return (
    <column id="login-password-group" gap={2}>
      <label id="login-password-label" fontSize={9}>{'密码'}</label>
      <input id="login-password" value={password.get()} placeholder="请输入密码" maxLength={64}
             height={12} disabled={submitting.get()}
             onChange={event => password.set(event.value == null ? '' : event.value)}
             onSubmit={() => submit()} />
    </column>
  );
}

export function renderLoginForm() {
  const message = error.get();
  return (
    <screen id="login-screen" title="欢迎登录" pausesGame={false} closeOnEscape={true}>
      <panel id="login-card" width={220} padding={12} gap={8}
             background="#F5F5F5FF" borderColor="#DDDDDDFF" borderWidth={1} radius={4}>
        <label id="login-title" fontSize={{ base: 10, profiles: { 6: 12 } }}>{'欢迎登录'}</label>
        <label id="login-subtitle" color="gray" fontSize={8}>{'请输入您的账号信息'}</label>
        <UsernameField />
        <PasswordField />
        <label id="login-error" color="#DC3545" fontSize={8}
               visible={message !== ''}>{message}</label>
        <row id="login-actions" gap={12}>
          <button id="login-submit" width="fill" disabled={submitting.get()}
                  onClick={() => submit()}>{submitting.get() ? '登录中…' : '登录'}</button>
          <button id="login-cancel" width="fill" disabled={submitting.get()}
                  onClick={() => cancel()}>{'取消'}</button>
        </row>
      </panel>
    </screen>
  );
}

export const conversion = Object.freeze({
  source: 'login-form.html',
  output: 'login-form.output.tsx',
  report: 'login-form.conversion-report.json'
});

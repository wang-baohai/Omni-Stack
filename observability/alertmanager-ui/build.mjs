import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { cpSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
const version = '0.30.1';
const archiveSha256 = 'b3577c531d4c05e0f45f90b02224c81684be9f4083720a01cc05bfa07107fc93';
const locales = ['en-US', 'zh-CN', 'ja-JP', 'ko-KR'];
const args = process.argv.slice(2);
const archive = resolve(args[args.indexOf('--archive') + 1] ?? '');
const output = resolve(args[args.indexOf('--output') + 1] ?? 'dist');
if (!args.includes('--archive') || !args.includes('--output')) {
  throw new Error('Usage: node build.mjs --archive /source.tar.gz --output /dist');
}
const sha256 = value => createHash('sha256').update(value).digest('hex');
if (sha256(readFileSync(archive)) !== archiveSha256) throw new Error('Upstream archive SHA-256 mismatch');
const messages = new Map();
for (const line of readFileSync(join(root, 'messages.tsv'), 'utf8').split(/\r?\n/u)) {
  if (!line || line.startsWith('#')) continue;
  const columns = line.split('|').map(value => value.replace(/\\u0020/gu, ' '));
  if (columns.length !== 4 || columns.some(value => !value.trim())) throw new Error(`Invalid translation row: ${line}`);
  const [key, ...translations] = columns;
  if (messages.has(key)) throw new Error(`Duplicate translation key: ${key}`);
  messages.set(key, [key, ...translations]);
}

// 仅替换已核对的界面文案；请求字段、路由、协议枚举和用户数据不在此清单中。
const targets = {
  'Views/NavBar/Types.elm': ['Alerts', 'Silences', 'Status', 'Settings', 'Help'],
  'Views/NavBar/Views.elm': ['New Silence'],
  'Views/AlertList/Views.elm': ['Group (custom)', 'Group', 'Filter', 'Silenced', 'Inhibited', 'Muted', 'Collapse all groups', 'Expand all groups', 'No alert groups found', 'Not grouped', 'Filter by this label', ' alert', ' alerts'],
  'Views/AlertList/AlertView.elm': ['Filter by this label', 'Link', 'Silenced', 'Silence', 'Inhibited', 'Muted'],
  'Views/ReceiverBar/Views.elm': ['Receiver: ', 'All'],
  'Views/ReceiverBar/Updates.elm': ['All'],
  'Views/GroupBar/Views.elm': ['Enable custom grouping', 'Label key for grouping alerts', 'Label key for grouping alerts, e.g.'],
  'Views/FilterBar/Views.elm': ['Silence', 'Custom matcher, e.g.'],
  'Views/SilenceForm/Views.elm': ['Edit Silence', 'New Silence', 'Creator', 'Comment', 'Cancel', 'Set Date/Time', 'Start', 'Duration', 'End', 'Matchers ', 'Alerts affected by this silence', 'Reset', 'Update', 'Create', 'Preview Alerts'],
  'Views/SilenceForm/Types.elm': ['Please complete adding the matcher', 'Matchers are required', "Can't be in the past"],
  'Views/SilenceForm/Updates.elm': ['Could not submit the form, Silence is not yet valid.', 'Cannot display affected Alerts, Silence is not yet valid.'],
  'Views/SilenceList/Views.elm': ['Filter', 'No silences found'],
  'Views/SilenceList/SilenceView.elm': ['Ends', 'Starts', 'Expired', 'Expire Silence', 'Are you sure you want to expire this silence?', 'Confirm', 'Edit', 'Recreate', 'Expire', 'Delete', 'View'],
  'Views/SilenceView/Views.elm': ['Silence', 'ID', 'Starts at', 'Ends at', 'Updated at', 'Created by', 'Comment', 'State', 'Matchers', 'Expire Silence', 'Are you sure you want to expire this silence?', 'Confirm', 'Expire', 'Delete'],
  'Views/Shared/SilencePreview.elm': ['No affected alerts', 'Affected alerts: '],
  'Views/Shared/Alert.elm': ['Info', 'Source'],
  'Views/Status/Views.elm': ['Status', 'Uptime:', 'Config', 'Cluster Status', 'Name:', 'Status:', 'Peers:', 'Name: ', 'Address: ', 'Version Information', 'Branch:', 'BuildDate:', 'BuildUser:', 'GoVersion:', 'Revision:', 'Version:'],
  'Views/Settings/Views.elm': ['First day of the week:', 'Note: This setting is saved in local storage of your browser'],
  'Views/NotFound/Views.elm': ['not found'],
  'Utils/DateTimePicker/Views.elm': ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Start', 'End'],
  'Utils/DateTimePicker/Utils.elm': ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December', 'could not convert string'],
  'Utils/Views.elm': ['Loading...'],
  'Utils/Date.elm': ['Wrong duration format', 'Should not be empty', 'Wrong ISO8601 format'],
  'Utils/FormValidation.elm': ['Should not be empty'],
  'Utils/Api.elm': ['Timeout exceeded', 'Network error', 'Unexpected response from api: ', 'Malformed url: '],
};

function replaceOnce(source, before, after, description) {
  if (source.split(before).length !== 2) throw new Error(`Structural patch mismatch: ${description}`);
  return source.replace(before, after);
}

function patchNativeUI(sourceRoot) {
  const changed = [];
  for (const [relativePath, keys] of Object.entries(targets)) {
    const path = join(sourceRoot, 'src', relativePath);
    const original = readFileSync(path, 'utf8');
    let text = original;
    for (const key of keys) {
      if (!messages.has(key)) throw new Error(`Missing translation: ${key}`);
      const literal = JSON.stringify(key);
      if (!text.includes(literal)) throw new Error(`UI source key changed: ${relativePath}: ${key}`);
      text = text.split(literal).join(`(I18n.t ${literal})`);
    }
    text = text.replace(/\n\n(?=import |type )/u, '\n\nimport I18n\n\n');
    if (!text.includes('import I18n')) throw new Error(`Cannot insert import: ${relativePath}`);
    if (relativePath === 'Views/Settings/Views.elm') {
      text = replaceOnce(text, '[ text radioValue ]', '[ text (I18n.t radioValue) ]', 'weekday display only');
    }
    if (relativePath === 'Views/SilenceList/Views.elm') {
      text = text.replaceAll('(stateToString tab)', '(I18n.t (stateToString tab))');
    }
    if (relativePath === 'Views/SilenceView/Views.elm') {
      text = replaceOnce(text, 'text <| stateToString silence.status.state', 'text <| I18n.t (stateToString silence.status.state)', 'silence state display only');
    }
    if (relativePath === 'Views/NavBar/Views.elm') {
      text = replaceOnce(text, 'Html, a, div, header, li, nav, text, ul', 'Html, a, div, header, label, li, nav, option, select, span, text, ul', 'selector elements');
      text = replaceOnce(text, '(class, href, style, title)', '(attribute, class, for, href, id, selected, style, title, value)', 'selector attributes');
      text = replaceOnce(text, 'import Types exposing (Route(..))', 'import Html.Events exposing (onInput)\nimport Types exposing (Msg(..), Route(..))', 'selector event');
      text = replaceOnce(text, 'navBar : Route -> Html msg', 'navBar : Route -> Html Msg', 'navbar message type');
      text = replaceOnce(text, ', case currentRoute of', ', languagePicker\n            , case currentRoute of', 'native selector placement');
      text += `\n\n-- 语言切换由 Elm 事件发出，保留官方页面的路由与筛选条件。\nlanguagePicker : Html Msg\nlanguagePicker =\n    label [ class "omni-language ml-3", for "omni-locale" ]\n        [ span [ class "mr-2" ] [ text (I18n.t "Language") ]\n        , select [ id "omni-locale", value I18n.locale, onInput SwitchLanguage ]\n            (List.map\n                (\\( code, name ) -> option [ value code, selected (code == I18n.locale) ] [ text name ])\n                [ ( "zh-CN", "简体中文" ), ( "en-US", "English" ), ( "ja-JP", "日本語" ), ( "ko-KR", "한국어" ) ]\n            )\n        ]\n`;
    }
    writeFileSync(path, text);
    changed.push({ path: relativePath, original_sha256: sha256(original), patched_sha256: sha256(text), translated_keys: keys });
  }
  const types = join(sourceRoot, 'src/Types.elm');
  writeFileSync(types, replaceOnce(readFileSync(types, 'utf8'), '    | SetGroupExpandAll Bool', '    | SetGroupExpandAll Bool\n    | SwitchLanguage String', 'language message'));
  const updates = join(sourceRoot, 'src/Updates.elm');
  let update = readFileSync(updates, 'utf8');
  update = replaceOnce(update, 'module Updates exposing (update)', 'port module Updates exposing (update)', 'language port module');
  update = replaceOnce(update, '    case msg of', `    case msg of\n        SwitchLanguage locale ->\n            if List.member locale [ "zh-CN", "en-US", "ja-JP", "ko-KR" ] then\n                ( model, switchLanguage locale )\n\n            else\n                ( model, Cmd.none )\n`, 'language event dispatch');
  writeFileSync(updates, update + '\n\n-- 浏览器端仅负责导航，不改写已渲染的界面文字。\nport switchLanguage : String -> Cmd msg\n');
  return changed;
}

const temp = mkdtempSync(join(tmpdir(), 'omni-alertmanager-ui-'));
execFileSync('tar', ['-xzf', archive, '-C', temp, `alertmanager-${version}/ui/app`, `alertmanager-${version}/LICENSE`, `alertmanager-${version}/NOTICE`]);
const upstream = join(temp, `alertmanager-${version}`);
mkdirSync(output, { recursive: true });
const receipt = { upstream: `prometheus/alertmanager@v${version}`, archive_sha256: archiveSha256, compiler: execFileSync('elm', ['--version'], { encoding: 'utf8' }).trim(), messages: messages.size, locales: [], native_ui: 'Elm', protocol_sources_unchanged: ['src/Data', 'src/Silences/Types.elm', 'src/Utils/Filter.elm', 'src/Views/Settings/Updates.elm'] };
for (const locale of locales) {
  const work = join(temp, locale);
  cpSync(join(upstream, 'ui/app'), work, { recursive: true });
  const changed = patchNativeUI(work);
  const index = locales.indexOf(locale);
  const cases = [...messages].map(([key, values]) => `        ${JSON.stringify(key)} ->\n            ${JSON.stringify(values[index])}`).join('\n\n');
  writeFileSync(join(work, 'src/I18n.elm'), `module I18n exposing (locale, t)\n\n\nlocale : String\nlocale =\n    ${JSON.stringify(locale)}\n\n\n-- 只处理本地界面文案；未匹配的服务端数据保持原值。\nt : String -> String\nt key =\n    case key of\n${cases}\n\n        _ ->\n            key\n`);
  const destination = join(output, locale);
  mkdirSync(destination, { recursive: true });
  execFileSync('elm', ['make', 'src/Main.elm', '--optimize', `--output=${join(destination, 'script.js')}`], { cwd: work, stdio: 'inherit' });
  let html = readFileSync(join(work, 'index.html'), 'utf8');
  html = replaceOnce(html, '<html lang="en">', `<html lang="${locale}">`, 'html language');
  html = replaceOnce(html, '</head>', `<style>\n.navbar .container{display:flex;flex-wrap:wrap;gap:.75rem;align-items:center}.omni-language{display:flex;align-items:center;margin-bottom:0}.omni-language select{max-width:10rem;padding:.35rem;border:1px solid #ccc;border-radius:.25rem}.nav-tabs{flex-wrap:wrap}.card-header .nav-item{max-width:100%}.card-header .custom-control-description{overflow-wrap:anywhere}.table{overflow-wrap:anywhere}pre{overflow:auto}button,a.btn{white-space:normal}\n@media(max-width:767px){.navbar .container{width:100%;padding:0 1rem}.navbar-nav{display:flex;flex-direction:row;flex-wrap:wrap;width:100%}.navbar-nav .nav-link{padding:.4rem .7rem}.omni-language{margin-left:0!important}.container{max-width:100%}.card-header .nav-item.ml-auto{margin-left:0!important}.modal-dialog{margin:.5rem;max-width:calc(100vw - 1rem)}.form-group .col-2{flex:0 0 33%;max-width:33%}.form-group .col-10{flex:0 0 67%;max-width:67%}.list-group-item>.d-flex{flex-wrap:wrap}}\n</style>\n</head>`, 'responsive native layout');
  html = replaceOnce(html, "            app.ports.persistDefaultCreator.subscribe", `            app.ports.switchLanguage.subscribe(function(locale) {\n                if (!['zh-CN', 'en-US', 'ja-JP', 'ko-KR'].includes(locale)) return;\n                localStorage.setItem('omniAlertmanagerLocale', locale);\n                location.assign('/' + locale + '/' + location.hash);\n            });\n            app.ports.persistDefaultCreator.subscribe`, 'navigation subscription');
  writeFileSync(join(destination, 'index.html'), html);
  receipt.locales.push({ locale, script_sha256: sha256(readFileSync(join(destination, 'script.js'))), html_sha256: sha256(html), patches: changed });
}
writeFileSync(join(output, 'index.html'), `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>Alertmanager</title></head><body><script>const locales=['zh-CN','en-US','ja-JP','ko-KR'];const saved=localStorage.getItem('omniAlertmanagerLocale');location.replace('/'+(locales.includes(saved)?saved:'zh-CN')+'/'+location.hash);</script></body></html>\n`);
cpSync(join(upstream, 'LICENSE'), join(output, 'UPSTREAM-LICENSE'));
cpSync(join(upstream, 'NOTICE'), join(output, 'UPSTREAM-NOTICE'));
writeFileSync(join(output, 'build-provenance.json'), JSON.stringify(receipt, null, 2) + '\n');
console.log(`Compiled official native Elm UI in ${locales.length} languages with ${messages.size} reviewed message keys.`);

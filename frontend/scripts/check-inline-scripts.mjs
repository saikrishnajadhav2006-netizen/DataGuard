import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const htmlPath = fileURLToPath(new URL('../public/dataguard-navy-violet.html', import.meta.url));
const html = readFileSync(htmlPath, 'utf8');
const inlineScripts = [...html.matchAll(/<script\b(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi)]
  .map((match, index) => ({ index, code: match[1] }))
  .filter((item) => item.code.trim());

for (const item of inlineScripts) {
  try {
    // Compile each inline classic script without executing DOM-dependent code.
    new Function(item.code);
  } catch (error) {
    throw new Error('Inline script #' + item.index + ' failed syntax validation: ' + error.message);
  }
}

if (!html.includes("$$('.thm').forEach(b=>b.onclick=mode)")) {
  throw new Error('Theme buttons must use the multi-element selector; otherwise page initialization can stop.');
}
if (/(?<!\$)\$\(['"]\.thm['"]\)\.forEach/.test(html)) {
  throw new Error('Found a single-element selector incorrectly used with forEach for theme buttons.');
}
if (!html.includes('window.parent.DATAGUARD_API_URL')) {
  throw new Error('The standalone page must read the configured API base from its same-origin React shell.');
}
if (!html.includes("document.querySelectorAll('.fc.si [data-login]')")) {
  throw new Error('The real email/password login handler is missing.');
}
if (!html.includes("document.querySelectorAll('.fc.su [data-login]')")) {
  throw new Error('The real email/password registration handler is missing.');
}

console.log('Validated ' + inlineScripts.length + ' inline scripts and critical dashboard/auth wiring.');

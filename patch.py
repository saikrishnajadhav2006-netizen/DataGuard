import os, re
path = 'frontend/src/pages/Dashboard.jsx'
with open(path, 'r') as f:
    content = f.read()

new_sv = """<span className=\"sv\" style={{ background: sevColor, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                      <Icon name={f.severity === 'CRITICAL' || f.severity === 'HIGH' ? 'alert-triangle' : 'info'} style={{width: 12, height: 12}} />
                      {f.severity}
                    </span>"""
content = re.sub(r'<span className=\"sv\" style=\{\{ background: sevColor \}\}>\{f\.severity\}</span>', new_sv, content)

new_rt = """<div className=\"rt\" style={{ marginTop: 8 }}>
                  <div><i style={{ background: localReview.qualityScore >= 80 ? 'var(--a)' : 'var(--warn)' }}><Icon name=\"shield\" style={{width: 14, height: 14, color: 'white'}} /></i>Quality</div>
                  <div><i style={{ background: localReview.securityScore >= 80 ? 'var(--a)' : localReview.securityScore >= 50 ? 'var(--warn)' : 'var(--a2)' }}><Icon name=\"shield\" style={{width: 14, height: 14, color: 'white'}} /></i>Security</div>
                  <div><i style={{ background: localReview.architectureScore >= 80 ? 'var(--a)' : 'var(--warn)' }}><Icon name=\"shield\" style={{width: 14, height: 14, color: 'white'}} /></i>Arch.</div>
                </div>"""
content = re.sub(r'<div className=\"rt\" style=\{\{ marginTop: 8 \}\}>.*?</div>\s*</div>\s*</div>', new_rt + '\n              </div>\n            </div>', content, flags=re.DOTALL)

with open(path, 'w') as f:
    f.write(content)
print('Patched Dashboard.jsx with Icons')

const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const root=__dirname,read=n=>JSON.parse(fs.readFileSync(path.join(root,'results',n)));
const java=read('java-summary.json'),api=read('postman-summary.json'),perf=read('performance-summary.json');
assert.equal(java.services.reduce((n,s)=>n+s.tests,0),192);
for(const s of java.services){assert.equal(s.failures+s.errors+s.skipped,0);for(const k of ['line','branch'])assert.equal(s[k].percent,100*s[k].covered/(s[k].covered+s[k].missed));}
assert.equal(api.stats.requests.total,33);assert.equal(api.stats.assertions.total,81);assert.equal(api.failures.length,0);
assert.equal(perf.runs.reduce((n,r)=>n+r.samples,0),4800);
for(const r of perf.runs){assert.equal(r.errors,0);assert.equal(r.samples,r.users*300);assert.ok(r.durationMs>30000);assert.ok(Number.isFinite(r.p95Ms));}
const xml=fs.readFileSync(path.join(root,'selenium/target/surefire-reports/TEST-com.eventflow.quality.JavaJourneysTest.xml'),'utf8');
const tag=xml.match(/<testsuite\b[^>]*>/)[0];for(const [key,value]of Object.entries({tests:4,failures:0,errors:0,skipped:0}))assert.equal(Number(tag.match(new RegExp(`\\b${key}="([^"]+)"`))[1]),value);
const privateSettings=JSON.parse(fs.readFileSync(path.join(root,'.runtime/settings.json')));
const forbidden=[privateSettings.password,privateSettings.dbPassword,privateSettings.jwt,privateSettings.roomKey,privateSettings.contractKey];
const checkTree=dir=>{for(const file of fs.readdirSync(dir,{withFileTypes:true})){const p=path.join(dir,file.name);if(file.isDirectory())checkTree(p);else if(/\.(json|xml|html|txt|log|jtl|csv)$/.test(file.name)){const t=fs.readFileSync(p,'utf8');for(const secret of forbidden)assert.ok(!t.includes(secret),`Private credential in ${p}`);assert.ok(!/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/.test(t),`Unmasked JWT in ${p}`);}}};
checkTree(path.join(root,'results'));
const result={date:new Date().toISOString(),junitCases:192,apiRequests:33,apiAssertions:81,seleniumJourneys:4,jmeterSamples:4800,secretCheck:'No campaign secrets or unmasked JWT in exported text reports',businessCode:'Unchanged; optional quality profiles only'};
if(fs.existsSync(path.join(root,'results/snyk-summary.json'))){
 const snyk=read('snyk-summary.json'),ids=new Set();assert.equal(snyk.services.length,3);
 for(const service of snyk.services){const raw=read(`snyk-${service.service}.json`),unique=new Map(raw.vulnerabilities.map(v=>[v.id,v]));assert.equal(raw.ok,false);assert.equal(service.exitCode,1);assert.equal(unique.size,service.uniqueAdvisories);const count={critical:0,high:0,medium:0,low:0};for(const v of unique.values()){count[v.severity]++;ids.add(v.id);}assert.deepEqual(count,service.severities);}
 assert.equal(ids.size,snyk.uniqueAdvisoriesAcrossServices);
 const zap=read('zap-summary.json'),alerts=read('zap-alerts.json').alerts;assert.equal(zap.requests.length,7);assert.ok(zap.requests.every(r=>r.status===200));assert.equal(zap.alerts,alerts.length);assert.equal(new Set(alerts.map(a=>a.pluginId)).size,3);
 result.security={snykProjects:3,snykUniqueAdvisories:ids.size,zapRequests:7,zapAlertInstances:alerts.length,zapAlertTypes:3,remediation:'Not performed'};
}
fs.writeFileSync(path.join(root,'results/verification.json'),JSON.stringify(result,null,2));console.log(result);

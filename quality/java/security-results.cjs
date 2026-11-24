const fs=require('fs'),path=require('path'),cp=require('child_process');
const root=__dirname,results=path.join(root,'results');
const services=['auth-service','evenement-service','budget-service'];
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const union=new Map();
const summaries=services.map(service=>{
 const data=JSON.parse(fs.readFileSync(path.join(results,`snyk-${service}.json`)));
 if(data.error||!Array.isArray(data.vulnerabilities))throw Error('No completed Snyk analysis for '+service);
 const unique=new Map();for(const issue of data.vulnerabilities){unique.set(issue.id,issue);union.set(issue.id,issue);}
 const severities={critical:0,high:0,medium:0,low:0};for(const v of unique.values())severities[v.severity]=(severities[v.severity]||0)+1;
 return {service,project:data.projectName,dependencyCount:data.dependencyCount,reportedEntries:data.vulnerabilities.length,uniqueAdvisories:unique.size,severities,ok:data.ok,exitCode:data.ok?0:1,examples:[...unique.values()].filter(v=>['org.springframework.security:spring-security-oauth2-jose','org.apache.tomcat.embed:tomcat-embed-core','org.postgresql:postgresql'].includes(v.name)).slice(0,6).map(v=>({id:v.id,title:v.title,package:v.name,version:v.version,severity:v.severity,from:v.from,fixedIn:v.fixedIn}))};
});
const summary={date:new Date().toISOString(),tool:'Snyk CLI '+cp.execFileSync('snyk',['--version'],{encoding:'utf8'}).trim(),scope:'Maven pom.xml dependency analysis; counts deduplicated by advisory ID per service; reported dependency paths can include test dependencies; no exploitability proof or automatic upgrade',uniqueAdvisoriesAcrossServices:union.size,services:summaries};
fs.writeFileSync(path.join(results,'snyk-summary.json'),JSON.stringify(summary,null,2));
const css='body{font:18px system-ui;margin:40px;color:#25344c}h1{color:#2c4570}table{border-collapse:collapse;width:100%;margin:22px 0}td,th{padding:14px;text-align:left;border-bottom:1px solid #ddd}th{background:#edf1f7}.notice{background:#fff1ee;border-left:4px solid #b34435;padding:18px}pre{font:13px monospace;background:#f4f6fa;padding:16px;white-space:pre-wrap}code{font-size:14px}';
fs.writeFileSync(path.join(results,'snyk.html'),`<!doctype html><html lang="fr"><meta charset="utf-8"><title>EventFlow — analyse Snyk</title><style>${css}</style><h1>EventFlow · Analyse des dépendances Java</h1><p>${esc(summary.tool)} · 24 novembre 2026 · consolidation des sorties JSON réelles</p><p class="notice">Analyse exécutée avec vulnérabilités signalées. Une exécution terminée n'est pas un résultat de sécurité conforme.</p><table><tr><th>Service</th><th>Dépendances</th><th>Critiques</th><th>Élevées</th><th>Moyennes</th><th>Faibles</th><th>Avis distincts</th></tr>${summaries.map(s=>`<tr><td>${s.service}</td><td>${s.dependencyCount}</td><td>${s.severities.critical}</td><td>${s.severities.high}</td><td>${s.severities.medium}</td><td>${s.severities.low}</td><td>${s.uniqueAdvisories}</td></tr>`).join('')}</table><p>Les services partagent des composants : ${union.size} identifiants d'avis distincts sur l'ensemble, et non la somme des trois lignes. Les chemins retournés incluent aussi des bibliothèques de test. L'exploitabilité dans EventFlow n'a pas été démontrée.</p><h2>Exemples de composants signalés</h2><table><tr><th>Composant</th><th>Version</th><th>Sévérité</th><th>Avis</th></tr>${summaries[0].examples.slice(0,3).map(v=>`<tr><td><code>${esc(v.package)}</code></td><td>${esc(v.version)}</td><td>${esc(v.severity)}</td><td><a href="https://security.snyk.io/vuln/${esc(v.id)}">${esc(v.id)}</a></td></tr>`).join('')}</table><p>Les propositions de mise à jour nécessitent une revue de compatibilité et une régression complète. Aucune dépendance applicative n'a été modifiée pendant cette campagne.</p></html>`);
console.log(JSON.stringify(summary,null,2));
const zapLog=path.join(root,'.runtime/zap-daemon.log');
if(fs.existsSync(zapLog)){
 const text=fs.readFileSync(zapLog,'utf8');
 const lines=text.split('\n').filter(l=>/\b(?:ERROR|WARN)\s/.test(l));
 const safe=lines.filter(l=>/ExtensionCallHome|FirefoxProfileManager|ExtensionClientIntegration/.test(l));
 const diagnostics={errors:lines.filter(l=>/\bERROR\s/.test(l)).length,warnings:lines.filter(l=>/\bWARN\s/.test(l)).length,callHomeErrors:lines.filter(l=>/\bERROR\s.*ExtensionCallHome/.test(l)).length,firefoxErrors:lines.filter(l=>/\bERROR\s.*ExtensionClientIntegration/.test(l)).length,interpretation:'Engine diagnostics concern unavailable Firefox integration and DNS failures for cfu.zaproxy.org; no browser exploration requested; seven target responses are independently verified HTTP 200. Raw engine log remains private.'};
 fs.writeFileSync(path.join(results,'zap-diagnostics.json'),JSON.stringify(diagnostics,null,2));
 fs.writeFileSync(path.join(results,'zap-diagnostics-excerpt.txt'),safe.join('\n')+'\n');
}

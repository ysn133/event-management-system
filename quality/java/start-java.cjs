const fs=require('fs'),path=require('path'),{spawn}=require('child_process');
const root=__dirname,repo=path.resolve(root,'../..'),runtime=path.join(root,'.runtime');
const c=JSON.parse(fs.readFileSync(path.join(runtime,'settings.json')));
const services=[['auth-service',46355],['evenement-service',46351],['budget-service',46354]];
const pids=[];
for(const[name,port]of services){
  const log=fs.openSync(path.join(runtime,`${name}.log`),'a');
  const env={...process.env,JAVA_HOME:'/usr/lib/jvm/java-21-openjdk-amd64',SERVER_ADDRESS:'127.0.0.1',PORT:String(port),
    DATABASE_URL:'jdbc:postgresql://127.0.0.1:46379/eventflow_quality',DATABASE_USERNAME:'ef_quality',
    DATABASE_PASSWORD:c.dbPassword,JWT_SECRET:c.jwt,JWT_ISSUER:'eventflow-quality',
    FRONTEND_ORIGIN:c.origin,BOOTSTRAP_USER_EMAIL:c.email,BOOTSTRAP_USER_PASSWORD:c.password,
    ORGANIZERS_VALIDATION_MODE:'local',EVENTS_VALIDATION_MODE:'http',EVENT_SERVICE_URL:'http://127.0.0.1:46351',
    ROOMS_INTEGRATION_MODE:'mock',PROVIDERS_INTEGRATION_MODE:'mock',
    ROOM_BUDGET_SERVICE_KEY:c.roomKey,CONTRACT_SERVICE_KEY:c.contractKey};
  const child=spawn('/usr/lib/jvm/java-21-openjdk-amd64/bin/java',
    ['-Xmx512m','-jar',path.join(repo,'services',name,'target',`${name}-0.0.1-SNAPSHOT.jar`)],
    {cwd:repo,env,detached:true,stdio:['ignore',log,log]});
  child.unref();fs.closeSync(log);pids.push({service:name,pid:child.pid,port});
}
const log=fs.openSync(path.join(runtime,'external-domain-fixtures.log'),'a');
const fixture=spawn(process.execPath,[path.join(root,'external-fixtures.cjs')],{cwd:repo,detached:true,stdio:['ignore',log,log]});
fixture.unref();fs.closeSync(log);pids.push({service:'non-java-test-doubles',pid:fixture.pid,port:46360});
fs.writeFileSync(path.join(runtime,'pids.json'),JSON.stringify(pids,null,2));
console.log('Started isolated processes:',pids.map(p=>`${p.service}:${p.port}`).join(', '));

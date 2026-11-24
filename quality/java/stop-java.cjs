const fs=require('fs'),path=require('path');
const file=path.join(__dirname,'.runtime/pids.json');
if(fs.existsSync(file)){
 for(const entry of JSON.parse(fs.readFileSync(file))){
  try{
   const cmd=fs.readFileSync(`/proc/${entry.pid}/cmdline`,'utf8');
   const expected=entry.service==='non-java-test-doubles'?path.join(__dirname,'external-fixtures.cjs'):`services/${entry.service}/target/${entry.service}-0.0.1-SNAPSHOT.jar`;
   if(!cmd.includes(expected))throw Error('PID reused; refused to stop '+entry.pid);
   process.kill(entry.pid,'SIGTERM');console.log('Stopped quality process',entry.service);
  }catch(e){if(e.code!=='ENOENT'&&e.code!=='ESRCH')throw e;}
 }
 fs.unlinkSync(file);
}

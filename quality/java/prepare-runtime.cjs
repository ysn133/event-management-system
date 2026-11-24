const fs=require('fs'),path=require('path'),crypto=require('crypto');
const root=__dirname,dir=path.join(root,'.runtime');fs.mkdirSync(dir,{recursive:true});
const file=path.join(dir,'settings.json');
if(!fs.existsSync(file)) {
  const rand=()=>crypto.randomBytes(32).toString('hex');
  const settings={dbPassword:rand(),jwt:rand(),roomKey:rand(),contractKey:rand(),
    email:'quality-java@eventflow.test',password:rand(),origin:'http://127.0.0.1:46380'};
  fs.writeFileSync(file,JSON.stringify(settings,null,2),{mode:0o600});
}
const c=JSON.parse(fs.readFileSync(file));
fs.writeFileSync(path.join(dir,'compose.env'),`QUALITY_DB_PASSWORD=${c.dbPassword}\n`,{mode:0o600});
console.log('Private quality settings ready; no production configuration is read.');

// UI bootstrap fixtures only: Salles, Prestataires, Invités are NOT under test.
const http=require('http');
http.createServer((req,res)=>{
  res.setHeader('Content-Type','application/json');
  if(req.url.startsWith('/api/v1/'))res.end(JSON.stringify({items:[],total:0,limit:20,offset:0}));
  else res.end('[]');
}).listen(46360,'127.0.0.1');

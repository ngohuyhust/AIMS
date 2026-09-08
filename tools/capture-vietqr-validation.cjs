// Read-only original DTO oracle, no bootstrap/environment/database.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),source=path.resolve(root,'../ISD.20252-25/src/backend');
require(source+'/node_modules/ts-node').register({project:source+'/tsconfig.json',transpileOnly:true});
require(source+'/node_modules/reflect-metadata');
const {ValidationPipe}=require(source+'/node_modules/@nestjs/common');
const {CreateVietqrPaymentDto}=require(source+'/src/payment/dto/create-vietqr-payment.dto.ts');
const {VietqrCallbackDto}=require(source+'/src/payment/dto/vietqr-callback.dto.ts');
(async()=>{
 const pipe=new ValidationPipe({whitelist:true,transform:true}),fixtures=[];
 const create={orderId:1,amount:132000,content:'AIMS 1'};
 const callback={bankaccount:'12345678',amount:132000,transType:'C',content:'AIMS 1',transactionid:'BANK-1',transactiontime:1700000000000,referencenumber:'REF-1',orderId:'1'};
 for(const [kind,metatype,valid] of [['CREATE',CreateVietqrPaymentDto,create],['CALLBACK',VietqrCallbackDto,callback]]) {
  const inputs=[{},valid,{...valid,ignored:true}];
  for(const key of Object.keys(valid)) for(const value of [null,'',0,-1,'1',true,{},[]]) inputs.push({...valid,[key]:value});
  if(kind==='CREATE') inputs.push({...valid,content:' '},{...valid,content:'á'},{...valid,content:'A'.repeat(24)},{...valid,amount:'132000.5'});
  for(const input of inputs) {const f={kind,input};try{f.expected=await pipe.transform(structuredClone(input),{type:'body',metatype});}catch(e){f.errors=e.getResponse().message;}fixtures.push(f);}
 }
 fs.writeFileSync(root+'/src/backend/src/test/resources/vietqr/validation.json',JSON.stringify(fixtures,null,2)+'\n');console.log(fixtures.length+' fixtures');
})().catch(e=>{console.error(e.message);process.exitCode=1;});

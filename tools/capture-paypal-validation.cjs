// Offline DTO oracle: imports source DTOs only, without Nest bootstrap or environment files.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),source=path.resolve(root,'../ISD.20252-25/src/backend');
require(source+'/node_modules/ts-node').register({project:source+'/tsconfig.json',transpileOnly:true});
require(source+'/node_modules/reflect-metadata');
const {ValidationPipe}=require(source+'/node_modules/@nestjs/common');
const types={CREATE:require(source+'/src/payment/dto/create-paypal-order.dto.ts').CreatePaypalOrderDto,
 CAPTURE:require(source+'/src/payment/dto/capture-paypal-order.dto.ts').CapturePaypalOrderDto,
 REFUND:require(source+'/src/payment/dto/refund-paypal-order.dto.ts').RefundOrderDto};
(async()=>{
 const fixtures=[],pipe=new ValidationPipe({whitelist:true});
 for(const [operation,metatype] of Object.entries(types)) {
  const inputs=[{},...[null,'',0,-1,'1',true,[],{},1,1.25].map(orderID=>({orderID,paypalOrderID:'PAYPAL-1',ignored:true}))];
  if(operation==='CAPTURE') inputs.push(...[null,'',4,' ',{},[]].map(paypalOrderID=>({orderID:1,paypalOrderID})));
  for(const input of inputs) {
   const fixture={operation,input};
   try {fixture.expected=await pipe.transform(structuredClone(input),{type:'body',metatype});}
   catch(e) {fixture.errors=e.getResponse().message;}
   fixtures.push(fixture);
  }
 }
 fs.writeFileSync(root+'/src/backend/src/test/resources/paypal/validation.json',JSON.stringify(fixtures,null,2)+'\n');
 console.log('Captured '+fixtures.length+' source PayPal DTO fixtures.');
})().catch(e=>{console.error(e.message);process.exitCode=1;});

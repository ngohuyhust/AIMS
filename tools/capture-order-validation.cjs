// Offline original DTO oracle; never starts Nest or loads environment/database configuration.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),source=path.resolve(root,'../ISD.20252-25/src/backend');
require(source+'/node_modules/ts-node').register({project:source+'/tsconfig.json',transpileOnly:true});
require(source+'/node_modules/reflect-metadata');
const {ValidationPipe}=require(source+'/node_modules/@nestjs/common');
const {DeliveryInfoDto}=require(source+'/src/order/dto/delivery-info.dto.ts');
const {PlaceOrderDto}=require(source+'/src/order/dto/place-order.dto.ts');
const delivery={receiverName:'Customer',email:'customer@example.test',phoneNumber:'0912345678',address:'Test address',province:'Hà Nội'};
const cartItems=[{productId:1,quantity:1}];
(async()=>{
  const cases=[],pipe=new ValidationPipe({whitelist:true,transform:true});
  const deliveries=[{},delivery,{...delivery,unknown:true},{...delivery,deliveryNotes:null},{...delivery,deliveryNotes:1},
    ...['receiverName','address','province'].flatMap(key=>[{...delivery,[key]:''},{...delivery,[key]:null},{...delivery,[key]:'😀'.repeat(key==='province'?101:256)}]),
    ...['+84912345678','091234567','0|12345678','0912345678\n','123',''].map(phoneNumber=>({...delivery,phoneNumber})),
    ...['a@b','a..b@example.test','a+b@example.test',''].map(email=>({...delivery,email}))];
  for(const placement of [false,true]) for(const value of deliveries) {
    const input=placement?{cartItems,deliveryInfo:value}:value,fixture={placement,input};
    try {fixture.expected=await pipe.transform(structuredClone(input),{type:'body',metatype:placement?PlaceOrderDto:DeliveryInfoDto});}
    catch(error) {fixture.errors=error.getResponse().message;} cases.push(fixture);
  }
  for(const input of [{},{cartItems},{cartItems,deliveryInfo:null},{cartItems:[],deliveryInfo:delivery},{cartItems:[{}],deliveryInfo:{}},{cartItems,deliveryInfo:5}]) {
    const fixture={placement:true,input};
    try {fixture.expected=await pipe.transform(structuredClone(input),{type:'body',metatype:PlaceOrderDto});}
    catch(error) {fixture.errors=error.getResponse().message;} cases.push(fixture);
  }
  const dir=root+'/src/backend/src/test/resources/order';fs.mkdirSync(dir,{recursive:true});
  fs.writeFileSync(dir+'/validation.json','[\n'+cases.map(c=>JSON.stringify(c)).join(',\n')+'\n]\n');
  console.log('Captured '+cases.length+' original order validation fixtures.');
})().catch(e=>{console.error(e.message);process.exitCode=1;});

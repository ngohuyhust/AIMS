// Offline source oracle: synthetic inputs only, no bootstrap, environment or database.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),source=path.resolve(root,'../ISD.20252-25/src/backend');
require(source+'/node_modules/ts-node').register({project:source+'/tsconfig.json',transpileOnly:true});
require(source+'/node_modules/reflect-metadata');
const {ValidationPipe}=require(source+'/node_modules/@nestjs/common');
const {CheckCartStockDto}=require(source+'/src/order/dto/check-cart-stock.dto.ts');
const {ShippingFeeDto}=require(source+'/src/order/dto/shipping-fee.dto.ts');
const {ShippingCalculatorService}=require(source+'/src/order/services/shipping-calculator.service.ts');
const {WeightOnlyShippingStrategy}=require(source+'/src/order/strategies/weight-shipping.strategy.ts');
const {VolumetricShippingStrategy}=require(source+'/src/order/strategies/volumetric-shipping.strategy.ts');
const valid=[{productId:1,quantity:2}];
(async()=>{
  const validation=[],pipe=new ValidationPipe({whitelist:true,transform:true});
  const bodies=[{}, {cartItems:null},{cartItems:[]},{cartItems:4},{cartItems:{}},{cartItems:[null]},
    {cartItems:[3]},{cartItems:[{}]},{cartItems:[{productId:'1',quantity:'2'}]},
    {cartItems:[{productId:0,quantity:-1}]},{cartItems:[{productId:1.5,quantity:2.5}]},
    {cartItems:valid},{cartItems:[{productId:1,quantity:2147483647},{productId:1,quantity:2}]},
    {cartItems:[{productId:1,quantity:2,unknown:1}],unknown:1},
    ...[null,7,'','Hà Nội','x'.repeat(100),'x'.repeat(101),'😀'.repeat(100),'😀'.repeat(101)].map(province=>({province,cartItems:valid}))];
  for(const shipping of [false,true]) for(const input of bodies) {
    const fixture={shipping,input};
    try { fixture.expected=await pipe.transform(structuredClone(input),{type:'body',metatype:shipping?ShippingFeeDto:CheckCartStockDto}); }
    catch(error) { fixture.errors=error.getResponse().message; }
    validation.push(fixture);
  }
  const shipping=[];
  for(const province of ['Hà Nội','hanoi','hn','TP.HCM','TP Hồ Chí Minh','tphcm','Hồ Chí Minh','Ho Chi Minh City','hcm','  HÀ..NỘI\t','\ufeffHN\u00a0','Đà Nẵng','','Hà Nội city'])
    for(const weight of [-1,0,0.5,0.500001,1,2,3,3.000001,3.5,3.500001,20])
      for(const subtotal of [99999.99,100000,100000.01])
        shipping.push({province,weight,subtotal,expected:new ShippingCalculatorService(new WeightOnlyShippingStrategy()).calculateShippingFee(province,weight,subtotal)});
  for(const volumetric of [false,true]) for(const dimensions of [undefined,{}, {length:50},{length:50,width:60,height:10},{length:10,width:10,height:10}])
    shipping.push({province:'Da Nang',weight:2,subtotal:50000,volumetric,dimensions,
      expected:new ShippingCalculatorService(volumetric?new VolumetricShippingStrategy():new WeightOnlyShippingStrategy()).calculateShippingFee('Da Nang',2,50000,dimensions)});
  const dir=root+'/src/backend/src/test/resources/cart';fs.mkdirSync(dir,{recursive:true});
  for(const [name,data] of Object.entries({validation,shipping})) fs.writeFileSync(dir+'/'+name+'.json','[\n'+data.map(row=>JSON.stringify(row)).join(',\n')+'\n]\n');
  console.log(`Captured ${validation.length} validation and ${shipping.length} shipping fixtures.`);
})().catch(e=>{console.error(e.message);process.exitCode=1;});

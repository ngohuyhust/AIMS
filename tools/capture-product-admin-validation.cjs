// Offline original ValidationPipe oracle; never starts Nest or loads environment/database config.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),source=path.resolve(root,'../ISD.20252-25/src/backend');
require(source+'/node_modules/ts-node').register({project:source+'/tsconfig.json',transpileOnly:true});
require(source+'/node_modules/reflect-metadata');
const {ValidationPipe}=require(source+'/node_modules/@nestjs/common');
const {CreateProductDto}=require(source+'/src/product/dto/create-product.dto.ts');
const {UpdateProductDto}=require(source+'/src/product/dto/update-product.dto.ts');
const base={productType:'BOOK',title:'Book',category:'Book',barcode:'fixture',weight:1,originalPrice:100,currentPrice:100,quantityInStock:0,
  book:{authors:'Author',coverType:'Hard',publisher:'Pub',publicationDate:'2025-02-01'}};
const fixtures=[
  {name:'empty-create',update:false,input:{}},
  {name:'empty-update',update:true,input:{}},
  {name:'numeric-strings',update:false,input:{...base,weight:'1',quantityInStock:'2'}},
  {name:'negative-integer',update:true,input:{quantityInStock:-1}},
  {name:'fractional-integer',update:true,input:{quantityInStock:1.5}},
  {name:'null-optionals',update:true,input:{description:null,book:{numPages:null}}},
  {name:'missing-book-fields',update:false,input:{...base,book:{}}},
  {name:'nested-track-error',update:true,input:{cd:{tracks:[{title:7,lengthSeconds:0}]}}},
  {name:'invalid-date',update:true,input:{dvd:{releaseDate:'invalid'}}},
  {name:'whitelist',update:false,input:{...base,unknown:true,book:{...base.book,unknown:true}}},
  {name:'valid-dvd',update:true,input:{dvd:{runtimeMinutes:90,studio:'Studio',releaseDate:'2025-03-01T00:00:00Z'}}},
];
(async()=>{
  const pipe=new ValidationPipe({whitelist:true,transform:true});
  for(const fixture of fixtures) {
    try { fixture.expected=await pipe.transform(structuredClone(fixture.input),{type:'body',metatype:fixture.update?UpdateProductDto:CreateProductDto}); }
    catch(error) { fixture.errors=error.getResponse().message; }
  }
  const dir=root+'/src/backend/src/test/resources/product-admin';fs.mkdirSync(dir,{recursive:true});
  fs.writeFileSync(dir+'/validation.json',JSON.stringify(fixtures,null,2)+'\n');
  console.log('Captured '+fixtures.length+' original DTO fixtures offline.');
})().catch(error=>{console.error(error.message);process.exitCode=1;});

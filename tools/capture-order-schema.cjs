// Diagnostic original TypeORM DDL only; no connection, bootstrap, .env or source writes.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),source=path.resolve(root,'../ISD.20252-25/src/backend');
require(source+'/node_modules/ts-node').register({project:source+'/tsconfig.json',transpileOnly:true});
require(source+'/node_modules/reflect-metadata');
const {DataSource,Table,TableForeignKey}=require(source+'/node_modules/typeorm');
const names=['product','media','book','cd','cd-track','dvd','newspaper','product-audit-log'];
const entities=names.flatMap(name=>Object.values(require(source+'/src/product/entities/'+name+'.entity.ts')));
entities.push(...['order','order-item','delivery-info','invoice'].flatMap(name=>Object.values(require(source+'/src/order/entities/'+name+'.entity.ts'))));
(async()=>{
  const db=new DataSource({type:'postgres',entities}); await db.buildMetadatas();
  const selected=db.entityMetadatas.filter(m=>['orders','order_items','delivery_info','invoices'].includes(m.tableName));
  const runner=db.createQueryRunner();
  const queries=selected.map(m=>runner.createTableSql(Table.create(m,db.driver),false).query);
  for(const m of selected) for(const f of m.foreignKeys) queries.push(runner.createForeignKeySql(Table.create(m,db.driver),TableForeignKey.create(f,db.driver)).query);
  const dir=root+'/src/backend/src/test/resources/order';fs.mkdirSync(dir,{recursive:true});
  fs.writeFileSync(dir+'/typeorm-schema.sql','-- Original TypeORM metadata, generated offline.\n'+queries.join(';\n')+';\n');
  console.log(queries.join(';\n')+';');
})().catch(error=>{console.error(error.message);process.exitCode=1;});

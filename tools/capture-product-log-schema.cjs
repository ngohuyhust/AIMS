// Diagnostic original TypeORM DDL only; no connection, bootstrap, .env or source writes.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),source=path.resolve(root,'../ISD.20252-25/src/backend');
require(source+'/node_modules/ts-node').register({project:source+'/tsconfig.json',transpileOnly:true});
require(source+'/node_modules/reflect-metadata');
const {DataSource,Table,TableForeignKey}=require(source+'/node_modules/typeorm');
const names=['product','media','book','cd','cd-track','dvd','newspaper','product-audit-log'];
const entities=names.flatMap(name=>Object.values(require(source+'/src/product/entities/'+name+'.entity.ts')));
(async()=>{
  const db=new DataSource({type:'postgres',entities}); await db.buildMetadatas();
  const metadata=db.entityMetadatas.find(m=>m.tableName==='product_logs');
  const table=Table.create(metadata,db.driver),runner=db.createQueryRunner();
  const queries=[runner.createTableSql(table,false).query,...metadata.foreignKeys.map(f=>runner.createForeignKeySql(table,TableForeignKey.create(f,db.driver)).query)];
  const dir=root+'/src/backend/src/test/resources/product-admin';fs.mkdirSync(dir,{recursive:true});
  fs.writeFileSync(dir+'/typeorm-schema.sql','-- Original TypeORM metadata, generated offline.\n'+queries.join(';\n')+';\n');
  console.log(queries.join(';\n')+';');
})().catch(error=>{console.error(error.message);process.exitCode=1;});

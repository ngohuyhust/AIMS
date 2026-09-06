/* Read original TypeScript in memory. Never import AppModule/main or load source .env.
 * Usage: AIMS_ORACLE_PASSWORD=... node tools/capture-catalog-oracle.cjs
 * Only a disposable localhost:55433/aims_oracle database is accepted, by construction.
 * Output is diagnostic evidence/fixtures, not an automatic Java migration.
 */
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const source = path.resolve(root, '../ISD.20252-25/src/backend');
require(source + '/node_modules/ts-node').register({project: source + '/tsconfig.json', transpileOnly: true});
require(source + '/node_modules/reflect-metadata');
const {DataSource} = require(source + '/node_modules/typeorm');
const load = p => require(source + '/src/product/' + p);
const entities = ['product','media','book','cd','cd-track','dvd','newspaper']
  .flatMap(name => Object.values(load('entities/' + name + '.entity.ts')));
async function main() {
  if (!process.env.AIMS_ORACLE_PASSWORD) throw new Error('AIMS_ORACLE_PASSWORD is required');
  const db = new DataSource({type:'postgres',host:'127.0.0.1',port:55433,
    database:'aims_oracle',username:'postgres',password:process.env.AIMS_ORACLE_PASSWORD,
    entities,synchronize:false,logging:false});
  await db.initialize();
  try {
    const sql = await db.driver.createSchemaBuilder().log();
    if (!sql.upQueries.length) throw new Error('Oracle requires a fresh disposable database');
    fs.mkdirSync(root+'/docs/module-1', {recursive:true});
    fs.writeFileSync(root+'/docs/module-1/typeorm-schema.sql',
      '-- Diagnostic DDL from original TypeORM metadata on an empty isolated database.\n'+
      sql.upQueries.map(q=>q.query+';').join('\n')+'\n');
    await db.synchronize(); // Only this disposable DB, seven catalog entities, no users.
    await db.query(fs.readFileSync(root+'/src/backend/src/test/resources/catalog/seed.sql','utf8'));
    const {ProductRepository} = load('product.repository.ts');
    const {ProductService} = load('product.service.ts');
    const {ProductTypeHandlerFactory} = load('handlers/product-type-handler.factory.ts');
    const {BookHandler,CdHandler,DvdHandler,NewspaperHandler} = load('handlers/concrete-handlers.ts');
    const handlers = new ProductTypeHandlerFactory([new BookHandler(),new CdHandler(),new DvdHandler(),new NewspaperHandler()]);
    const service = new ProductService(db,new ProductRepository(db),null,handlers);
    const dir=root+'/src/backend/src/test/resources/catalog';
    for (const id of [1,2,3,4,5,7]) {
      fs.writeFileSync(dir+'/detail-'+id+'.json',JSON.stringify(await service.getProductById(id),null,2)+'\n');
    }
    fs.writeFileSync(dir+'/search.json',JSON.stringify(await service.searchProducts({}),null,2)+'\n');
    console.log('Captured catalog JSON and TypeORM schema from isolated database only.');
  } finally { await db.destroy(); }
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});

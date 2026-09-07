/* Generate diagnostic SQL from the installed original TypeORM metadata, entirely offline.
 * No connection, AppModule, bootstrap, .env loading or source writes.
 */
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const source = path.resolve(root, '../ISD.20252-25/src/backend');
require(source + '/node_modules/ts-node').register({project: source + '/tsconfig.json', transpileOnly: true});
require(source + '/node_modules/reflect-metadata');
const {DataSource, Table, TableForeignKey} = require(source + '/node_modules/typeorm');
const {User} = require(source + '/src/user/entities/user.entity.ts');
const {Role} = require(source + '/src/user/entities/role.entity.ts');
const {UserAuditLog} = require(source + '/src/user/entities/user-log.entity.ts');
async function main() {
  const db = new DataSource({type: 'postgres', entities: [User, Role, UserAuditLog]});
  await db.buildMetadatas();
  const runner = db.createQueryRunner();
  const tables = db.entityMetadatas.map(metadata => {
    const table = Table.create(metadata, db.driver);
    table.foreignKeys = metadata.foreignKeys.map(key => TableForeignKey.create(key, db.driver));
    return table;
  });
  const queries = tables.map(table => runner.createTableSql(table, false).query);
  for (const table of tables) {
    for (const index of table.indices) queries.push(runner.createIndexSql(table, index).query);
    for (const key of table.foreignKeys) queries.push(runner.createForeignKeySql(table, key).query);
  }
  const dir = root + '/src/backend/src/test/resources/user';
  fs.mkdirSync(dir, {recursive: true});
  fs.writeFileSync(dir + '/typeorm-schema.sql',
    '-- Generated offline from original TypeORM 0.3.29 user entity metadata.\n' + queries.join(';\n') + ';\n');
  console.log('Captured original user schema without opening a database connection.');
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });

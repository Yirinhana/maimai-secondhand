"""Turn information_schema TSV exports (no row data) into a course-ready dictionary.
Usage: python scripts/export-schema-docs.py columns.tsv relations.tsv migration_version
SQL exports must be taken from the isolated maimai_test schema after migrations.
"""
from pathlib import Path
import csv
import datetime
import sys

root = Path(__file__).resolve().parent.parent
def rows(path):
    with Path(path).open(encoding='utf-8-sig', newline='') as file:
        return list(csv.DictReader(file, delimiter='\t'))
columns, relations = rows(sys.argv[1]), rows(sys.argv[2])
migration_version = sys.argv[3]
if not migration_version.isdecimal():
    raise ValueError('migration_version must be the numeric applied Flyway version')
groups = {}
for column in columns:
    groups.setdefault(column['TABLE_NAME'], []).append(column)
def cell(value):
    return value.replace('|', '\\|').replace('\n', '<br>')
content = ['# 数据字典（实际迁移结构）', '',
           '生成时间：' + datetime.datetime.now().astimezone().isoformat(timespec='seconds') + '。', '',
           f'数据源为本项目独立 maimai_test 的 information_schema，仅提取结构，不含用户、密码、订单或聊天行数据。当前已应用迁移至 V{migration_version}；后续迁移后应重新导出。默认值 [NULL] 表示元数据未提供具体默认值，应结合可空列与服务代码理解。', '',
           f'共 {len(groups)} 张表、{len(columns)} 个字段；包括业务表、Spring Session 会话表和 Flyway 迁移历史表。PRl/PRI为主键，UNI为唯一索引，MUL为非唯一索引标记；完整复合键与约束以SQL迁移为准。'.replace('PRl/PRI','PRI'), '']
for table, fields in groups.items():
    content += ['## ' + table, '', '| 字段 | 类型 | 可空 | 键标记 | 默认值 | 附加属性 | 说明 |',
                '| --- | --- | --- | --- | --- | --- | --- |']
    for field in fields:
        content.append('| ' + ' | '.join(cell(field[name]) for name in
            ('COLUMN_NAME','COLUMN_TYPE','IS_NULLABLE','COLUMN_KEY','COLUMN_DEFAULT','EXTRA','COLUMN_COMMENT')) + ' |')
    refs = [rel for rel in relations if rel['TABLE_NAME'] == table]
    if refs:
        content += ['', '外键：' + '；'.join('`'+rel['COLUMN_NAME']+'` → `'+rel['REFERENCED_TABLE_NAME']+'.'+rel['REFERENCED_COLUMN_NAME']+'`' for rel in refs) + '。']
    content += ['']
output = root / 'docs/design/data-dictionary.md'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text('\n'.join(content), encoding='utf-8')
print(f'Exported {len(groups)} tables and {len(columns)} columns to {output}')

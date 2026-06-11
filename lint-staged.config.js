module.exports = {
  '*.{js,ts,vue}': ['npx eslint --fix', 'prettier --write'],
  '*.java': ['echo "Java formatting skipped"'],
  '*.{sql,yml,yaml,json,md}': ['prettier --write']
}
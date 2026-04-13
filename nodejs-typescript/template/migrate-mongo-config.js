{{- if eq stack_profile "nosql-cache" }}
module.exports = {
  mongodb: {
    url: process.env.APP_MONGO_URI || 'mongodb://localhost:27017',
    databaseName: process.env.APP_MONGO_DATABASE || '{{app_name}}',
    options: {},
  },
  migrationsDir: 'migrations',
  changelogCollectionName: 'changelog',
  migrationFileExtension: '.js',
  useFileHash: false,
  moduleSystem: 'commonjs',
};
{{- end }}

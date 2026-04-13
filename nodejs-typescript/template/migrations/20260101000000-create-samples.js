{{- if eq stack_profile "nosql-cache" }}
module.exports = {
  async up(db) {
    await db.createCollection('samples');
    await db.collection('samples').createIndex({ name: 1 }, { unique: true });
  },

  async down(db) {
    await db.collection('samples').drop();
  },
};
{{- end }}

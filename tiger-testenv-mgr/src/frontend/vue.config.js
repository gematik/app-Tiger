const {defineConfig} = require("@vue/cli-service");
module.exports = defineConfig({
  lintOnSave: process.env.NODE_ENV !== "production",
  devServer: {
    proxy: {
      "^/testEnv": {
        target: "http://localhost:8098/",
        ws: true,
        changeOrigin: true,
      },
      "^/status": {
        target: "http://localhost:8098/",
      },
    },
  },
  transpileDependencies: true,
});

module.exports = {
    root: true,
    env: {
        node: true,
        "vue/setup-compiler-macros": true,
        es2022: true
    },
    extends: [
        "plugin:vue/vue3-essential",
        "eslint:recommended",
        "plugin:@typescript-eslint/eslint-recommended",
        "@vue/typescript/recommended",
    ],
    rules: {
        "no-console": process.env.NODE_ENV === "production" ? "warn" : "off",
        "no-debugger": process.env.NODE_ENV === "production" ? "warn" : "off",
        "@typescript-eslint/no-inferrable-types": "off",
        "vue/multi-word-component-names": "off",
        "no-control-regex": 0,
    },
};

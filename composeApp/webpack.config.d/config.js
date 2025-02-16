/*
module.exports = env => {
    // example config
    (function(env, config) {
        const webpack = require("webpack");
        console.log("This is generated from Kotlin Webpack");
        const definePlugin = new webpack.DefinePlugin({
            ENVIRONMENT: env.DATA
        });
        config.plugins.push(definePlugin);
    })(env, config);
    return config;
}
*/
// trying to get source maps to work
// reference: https://discuss.kotlinlang.org/t/getting-source-maps-to-work/6640/5
config.module.rules.push({
    test: /\.js$/,
    loader: "source-map-loader",
    enforce: "pre"
});

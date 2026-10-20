# Relay 前端

Relay（订单同步中台）的管理界面。Vue 3 + Vite + Element Plus。

## 跑起来

```bash
npm install
npm run dev
```

打开 http://localhost:5173。

**注意：前端不自带数据，它只是个壳。** 起之前先把两个后端都跑起来：

| 服务 | 端口 | 仓库 |
|---|---|---|
| Relay（中台本体） | 8080 | `D:\Dsers` |
| 供应商（模拟供货方） | 8081 | `D:\dsers-supplier` |

后端没起的话页面能打开，但每个表格都会弹「请求失败」。

## 接口怎么走

浏览器直连后端会跨域，所以统一走 `/api` 前缀，由 Vite 代理掉（见 `vite.config.js`）：

- `/api/*` → `localhost:8080`
- `/supplier-api/*` → `localhost:8081`

**`/supplier-api` 是唯一的例外。** 正常情况下前端只跟 Relay 说话，下单、查物流这些业务动作全都经过中台 —— 前端绕过中台直接指挥供应商，那中台就不叫中台了。

只有一个地方非绕不可：商品页要展示「供应商货架」让用户挑货导入，而 Relay 目前只提供了「按 id 导入」，没有「列出供应商商品」的转发接口。所以这一处直连了供应商服务。

## 目录

```
src/
├── api/          每个模块一个文件，全部走 http.js 那个 axios 实例
├── layout/       侧边栏 + 顶栏的外框
├── router/       5 条路由，详情页靠 meta.parent 让列表页保持高亮
├── styles/       全局样式
├── utils/dict.js 状态字典、金额和时间格式化
└── views/        5 个页面
```

## 一个约定

后端的响应契约是 `{code, msg, data}`，**HTTP 状态码永远是 200**，成功失败看 `code`。

这件事在 `api/http.js` 的响应拦截器里统一处理掉了：`code === 200` 就把 `data` 直接返回给调用方，否则弹一句 `msg` 然后 reject。所以页面里拿到的就是干净的业务数据，不用每处都写一遍 `if (res.code === 200)`。

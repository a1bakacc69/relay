<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOrder, placeOrder, refreshShipping, pushTracking } from '../api/order'
import { orderStatus, money, dt } from '../utils/dict'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
/** 哪个按钮正在跑，用来只让那一个转圈，别把整页锁死 */
const busy = ref('')
const order = ref(null)

async function load() {
  loading.value = true
  try {
    order.value = await getOrder(route.params.id)
  } finally {
    loading.value = false
  }
}

/**
 * 闭环四步。每一步做完，状态才往后挪一格 ——
 * 所以「现在卡在哪」这件事，看这个条就够了，不用去记订单状态的含义。
 */
const STEPS = [
  { key: 'PENDING', name: '拉单入库', desc: '从平台取回订单' },
  { key: 'ORDERED', name: '下单供应商', desc: '把货交给供货方' },
  { key: 'SHIPPED', name: '供应商发货', desc: '拿到运单号' },
  { key: 'SYNCED', name: '回写平台', desc: '同步发货状态' }
]

const STATUS_INDEX = { PENDING: 1, ORDERED: 2, SHIPPED: 3, SYNCED: 4 }

/** 已经走到第几步。0 表示订单还没读到 */
const cur = computed(() => STATUS_INDEX[order.value?.status] ?? 0)

/**
 * 三个动作按顺序排开，没轮到的一律置灰。
 * 不是「藏起来」而是「看得见但点不了」—— 让用户知道后面还有几步，
 * 也才知道自己现在这一步在整个流程里的位置。
 */
const actions = computed(() => {
  const s = order.value?.status
  return [
    {
      key: 'place',
      label: '下单给供应商',
      hint: '把订单转给供货方，占他的库存',
      enabled: s === 'PENDING',
      disabledHint: s === undefined ? '' : '这一步已经做过了',
      run: () => placeOrder(order.value.id)
    },
    {
      key: 'refresh',
      label: '查物流',
      hint: '问供应商发货了没，有运单号就收下',
      enabled: s === 'ORDERED',
      disabledHint: s === 'PENDING' ? '还没下单，供应商那边没有这单' : '物流已经拿到了',
      run: () => refreshShipping(order.value.id)
    },
    {
      key: 'push',
      label: '回写平台',
      hint: '把运单号送回 Shopify，买家那边就显示已发货',
      enabled: s === 'SHIPPED',
      disabledHint: s === 'SYNCED' ? '已经回写过了' : '还没拿到运单号，没东西可回写',
      run: () => pushTracking(order.value.id)
    }
  ]
})

/** 当前该点的那个动作 —— 主按钮就它，其它两个是次要样式 */
const nextKey = computed(() => actions.value.find((a) => a.enabled)?.key || '')

async function run(action) {
  busy.value = action.key
  try {
    const msg = await action.run()
    ElMessage.success(msg || '完成')
    await load()
  } catch (e) {
    // 真正的失败原因（比如「这几个变体还没建映射」）已经由 axios 拦截器弹出来了，
    // 这里只是把按钮的转圈停掉
  } finally {
    busy.value = ''
  }
}

const subtotal = (it) => Number(it.price || 0) * (it.quantity || 0)

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="back">
      <el-button text @click="router.push('/orders')">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回订单列表</span>
      </el-button>
    </div>

    <template v-if="order">
      <!-- ---------- 闭环进度 ---------- -->
      <el-card shadow="never" class="panel">
        <div class="head">
          <div class="head-left">
            <span class="order-no">#{{ order.orderNumber }}</span>
            <el-tag :type="orderStatus(order.status).type" effect="light" round>
              {{ orderStatus(order.status).label }}
            </el-tag>
          </div>
          <span class="muted">{{ order.platform }} · {{ dt(order.createdAt) }}</span>
        </div>

        <div class="flow">
          <template v-for="(s, i) in STEPS" :key="s.key">
            <div class="step" :class="{ done: i < cur, active: i === cur }">
              <div class="dot">
                <el-icon v-if="i < cur"><Check /></el-icon>
                <span v-else>{{ i + 1 }}</span>
              </div>
              <div class="step-name">{{ s.name }}</div>
              <div class="step-desc">{{ s.desc }}</div>
            </div>
            <div v-if="i < STEPS.length - 1" class="line" :class="{ done: i < cur }" />
          </template>
        </div>

        <div class="actions">
          <el-button
            v-for="a in actions"
            :key="a.key"
            :type="a.key === nextKey ? 'primary' : 'default'"
            :disabled="!a.enabled || (busy && busy !== a.key)"
            :loading="busy === a.key"
            @click="run(a)"
          >
            {{ a.label }}
          </el-button>
          <span class="action-hint">
            {{ actions.find((a) => a.enabled)?.hint || '这一单已经走完了' }}
          </span>
        </div>
      </el-card>

      <div class="cols">
        <!-- ---------- 商品明细 ---------- -->
        <el-card shadow="never" class="panel">
          <template #header>
            <span class="card-title">商品明细</span>
            <span class="muted">{{ (order.items || []).length }} 项</span>
          </template>

          <el-table :data="order.items || []" size="small">
            <el-table-column label="商品" min-width="220">
              <template #default="{ row }">
                <div class="item-title">{{ row.title }}</div>
                <div class="item-sub">
                  <span v-if="row.variantTitle">{{ row.variantTitle }}</span>
                  <span v-else class="muted">无规格</span>
                  <span class="mono dim">变体 {{ row.variantId }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="单价" width="100" align="right">
              <template #default="{ row }">{{ money(row.price) }}</template>
            </el-table-column>

            <el-table-column label="数量" width="70" align="center">
              <template #default="{ row }">×{{ row.quantity }}</template>
            </el-table-column>

            <el-table-column label="小计" width="110" align="right">
              <template #default="{ row }">
                <span class="amount">{{ money(subtotal(row)) }}</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="total">
            <span>合计</span>
            <span class="total-num">{{ money(order.totalPrice) }}</span>
          </div>
        </el-card>

        <!-- ---------- 订单信息 ---------- -->
        <el-card shadow="never" class="panel side">
          <template #header>
            <span class="card-title">订单信息</span>
          </template>

          <div class="kv">
            <div class="k">订单号</div>
            <div class="v mono">{{ order.orderNumber }}</div>

            <div class="k">买家</div>
            <div class="v">{{ order.email || '-' }}</div>

            <div class="k">金额</div>
            <div class="v">{{ money(order.totalPrice) }} {{ order.currency }}</div>

            <div class="k">来源平台</div>
            <div class="v">{{ order.platform }}</div>

            <div class="k">平台支付状态</div>
            <div class="v muted">{{ order.financialStatus || '-' }}</div>

            <div class="k">平台发货状态</div>
            <div class="v muted">{{ order.fulfillmentStatus || '-' }}</div>

            <div class="k">运单号</div>
            <div class="v">
              <span v-if="order.trackingNumber" class="mono">{{ order.trackingNumber }}</span>
              <span v-else class="muted">还没有</span>
            </div>

            <div class="k">下单时间</div>
            <div class="v muted">{{ dt(order.createdAt) }}</div>
          </div>
        </el-card>
      </div>
    </template>

    <el-empty v-else-if="!loading" description="订单不存在" />
  </div>
</template>

<style scoped>
.back {
  margin-bottom: 6px;
}

.panel {
  border-radius: 10px;
}

.panel :deep(.el-card__header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  border-bottom: 1px solid #f0f2f5;
}

.card-title {
  font-weight: 600;
  color: #1f2d3d;
}

/* ---------- 顶部标题行 ---------- */
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
}

.head-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.order-no {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
}

/* ---------- 进度条 ---------- */
.flow {
  display: flex;
  align-items: flex-start;
  padding: 0 6px;
}

.step {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 116px;
  flex-shrink: 0;
}

.dot {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #f0f2f5;
  color: #c0c4cc;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  transition: all 0.2s;
}

.step.done .dot {
  background: #67c23a;
  color: #fff;
}

.step.active .dot {
  background: #409eff;
  color: #fff;
  /* 当前这步给一圈光晕，视线会自己落上去 */
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.16);
}

.step-name {
  margin-top: 9px;
  font-size: 13px;
  color: #c0c4cc;
  font-weight: 500;
}

.step.done .step-name {
  color: #67c23a;
}

.step.active .step-name {
  color: #409eff;
}

.step-desc {
  margin-top: 3px;
  font-size: 11px;
  color: #c0c4cc;
  text-align: center;
  line-height: 1.4;
}

.line {
  flex: 1;
  height: 2px;
  background: #f0f2f5;
  margin-top: 14px;
  border-radius: 1px;
}

.line.done {
  background: #67c23a;
}

/* ---------- 操作区 ---------- */
.actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px dashed #ebeef5;
}

.action-hint {
  font-size: 12.5px;
  color: #909399;
  margin-left: 4px;
}

/* ---------- 下方两栏 ---------- */
.cols {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 16px;
  margin-top: 16px;
}

.item-title {
  color: #1f2d3d;
}

.item-sub {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 3px;
  font-size: 11.5px;
  color: #909399;
}

.dim {
  color: #c0c4cc;
}

.amount {
  font-weight: 600;
}

.total {
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 14px;
  padding: 14px 4px 2px;
  border-top: 1px solid #f0f2f5;
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

.total-num {
  font-size: 19px;
  font-weight: 700;
  color: #1f2d3d;
}

/* ---------- 右侧键值对 ---------- */
.kv {
  display: grid;
  grid-template-columns: 88px 1fr;
  row-gap: 13px;
  font-size: 13px;
}

.kv .k {
  color: #909399;
}

.kv .v {
  color: #1f2d3d;
  word-break: break-all;
}

.side :deep(.el-card__body) {
  padding: 18px;
}
</style>

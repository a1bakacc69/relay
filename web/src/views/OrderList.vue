<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listOrders, pullOrders } from '../api/order'
import { orderStatus, money, dt } from '../utils/dict'

const router = useRouter()

const loading = ref(false)
const pulling = ref(false)
const orders = ref([])

async function load() {
  loading.value = true
  try {
    orders.value = (await listOrders()) || []
  } finally {
    loading.value = false
  }
}

async function onPull() {
  pulling.value = true
  try {
    // 拉单是个会真的打平台接口的动作，可能慢，也可能被平台限流。
    // 拉完不管成没成，都重新读一次列表 —— 拉回来一半也算数。
    const msg = await pullOrders()
    ElMessage.success(msg || '拉取完成')
    await load()
  } finally {
    pulling.value = false
  }
}

/** 四个状态各几单，做成顶部的小卡片：一屏之内就知道今天该先干什么 */
const STAT_CARDS = [
  { key: 'PENDING', label: '待下单' },
  { key: 'ORDERED', label: '已下单' },
  { key: 'SHIPPED', label: '已发货' },
  { key: 'SYNCED', label: '已回写' }
]

const stats = computed(() => {
  const c = { PENDING: 0, ORDERED: 0, SHIPPED: 0, SYNCED: 0 }
  orders.value.forEach((o) => {
    if (c[o.status] !== undefined) c[o.status] += 1
  })
  return c
})

/** 还没走完闭环的单数。这个数字是 0 的时候，整个系统是真的没事干 */
const todo = computed(() => orders.value.length - stats.value.SYNCED)

const goDetail = (row) => router.push(`/orders/${row.id}`)

onMounted(load)
</script>

<template>
  <div>
    <div class="stats">
      <div v-for="s in STAT_CARDS" :key="s.key" class="stat" :class="s.key.toLowerCase()">
        <div class="stat-num">{{ stats[s.key] }}</div>
        <div class="stat-label">{{ s.label }}</div>
      </div>
    </div>

    <el-card shadow="never" class="panel">
      <div class="toolbar">
        <div class="left">
          <el-button type="primary" :loading="pulling" @click="onPull">
            <el-icon><Download /></el-icon>
            <span>从平台拉单</span>
          </el-button>
          <span class="hint">
            从 Shopify 拉最新订单，新单会落成「待下单」
          </span>
        </div>
        <el-button text :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>
          <span>刷新</span>
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="orders"
        row-key="id"
        class="clickable"
        empty-text="还没有订单，点上面的「从平台拉单」试试"
        @row-click="goDetail"
      >
        <el-table-column label="订单号" width="140">
          <template #default="{ row }">
            <div class="order-no">#{{ row.orderNumber }}</div>
            <div class="order-sub">{{ (row.items || []).length }} 件商品</div>
          </template>
        </el-table-column>

        <el-table-column label="买家" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="muted">{{ row.email || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">
            <span class="amount">{{ money(row.totalPrice) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="运单号" width="170">
          <template #default="{ row }">
            <span v-if="row.trackingNumber" class="mono">{{ row.trackingNumber }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="orderStatus(row.status).type" effect="light" round>
              {{ orderStatus(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="下单时间" width="180">
          <template #default="{ row }">
            <span class="muted">{{ dt(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="goDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
/* ---------- 顶部四张小卡片 ---------- */
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}

.stat {
  background: #fff;
  border-radius: 10px;
  padding: 16px 20px;
  border-left: 3px solid #dcdfe6;
}

.stat-num {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.1;
  color: #1f2d3d;
}

.stat-label {
  font-size: 12.5px;
  color: #909399;
  margin-top: 6px;
}

/* 每张卡片左边的色条跟状态标签同色，看久了能形成条件反射 */
.stat.pending { border-left-color: #909399; }
.stat.ordered { border-left-color: #e6a23c; }
.stat.shipped { border-left-color: #409eff; }
.stat.synced  { border-left-color: #67c23a; }

.stat.pending .stat-num { color: #909399; }
.stat.ordered .stat-num { color: #e6a23c; }
.stat.shipped .stat-num { color: #409eff; }
.stat.synced  .stat-num { color: #67c23a; }

/* ---------- 表格 ---------- */
.panel :deep(.el-card__body) {
  padding: 0;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid #f0f2f5;
}

.toolbar .left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.hint {
  font-size: 12.5px;
  color: #a8abb2;
}

.order-no {
  font-weight: 600;
  color: #1f2d3d;
}

.order-sub {
  font-size: 11.5px;
  color: #c0c4cc;
  margin-top: 2px;
}

.amount {
  font-weight: 600;
  color: #1f2d3d;
}

/* 整行可点，鼠标移上去给个手感 */
.clickable :deep(.el-table__row) {
  cursor: pointer;
}
</style>

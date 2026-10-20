<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listMappings,
  listUnboundVariants,
  listLocalVariants,
  createMapping,
  deleteMapping
} from '../api/mapping'
import { money, dt } from '../utils/dict'

const loading = ref(false)
const saving = ref(false)

const unbound = ref([])
const bound = ref([])
const locals = ref([])

const dialog = reactive({ open: false, row: null, localVariantId: null })

async function load() {
  loading.value = true
  try {
    // 三份数据一起拉：左边「还差什么」、右边「能拿什么去配」、下面「已经配好了什么」
    const [u, b, l] = await Promise.all([
      listUnboundVariants(),
      listMappings(),
      listLocalVariants()
    ])
    unbound.value = u || []
    bound.value = b || []
    locals.value = l || []
  } finally {
    loading.value = false
  }
}

/** 出现次数最多的排前面 —— 先绑最要紧的那几个 */
const sortedUnbound = computed(() =>
  [...unbound.value].sort((a, b) => (b.occurrences || 0) - (a.occurrences || 0))
)

function openBind(row) {
  dialog.row = row
  dialog.localVariantId = null
  dialog.open = true
}

async function confirmBind() {
  if (!dialog.localVariantId) {
    ElMessage.warning('先选一条我们的规格')
    return
  }
  saving.value = true
  try {
    const msg = await createMapping({
      localVariantId: dialog.localVariantId,
      platform: dialog.row.platform,
      platformVariantId: dialog.row.variantId
    })
    ElMessage.success(msg || '绑定成功')
    dialog.open = false
    await load()
  } catch (e) {
    // 拦截器弹过了
  } finally {
    saving.value = false
  }
}

async function onUnbind(row) {
  try {
    await ElMessageBox.confirm(
      `解绑之后，${row.platform} 的变体 ${row.platformVariantId} 再出现在订单里，下单会直接报错。确认解绑？`,
      '解绑映射',
      { type: 'warning', confirmButtonText: '解绑', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户点了取消
  }

  try {
    const msg = await deleteMapping(row.id)
    ElMessage.success(msg || '已解绑')
    await load()
  } catch (e) {
    // 拦截器弹过了
  }
}

/** 下拉里给每条规格配一句说明，光看「500ml」认不出是哪件货 */
const localLabel = (v) => `${v.productTitle} · ${v.variantTitle || '默认规格'}`

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <!-- ---------- 还没绑的 ---------- -->
    <el-card v-if="unbound.length" shadow="never" class="panel alert">
      <template #header>
        <div class="head">
          <div class="head-left">
            <el-icon class="warn-icon"><WarningFilled /></el-icon>
            <span class="card-title">有 {{ unbound.length }} 个变体还没绑定</span>
          </div>
          <el-button text :loading="loading" @click="load">
            <el-icon><Refresh /></el-icon>
            <span>刷新</span>
          </el-button>
        </div>
      </template>

      <p class="explain">
        这些是<b>真实订单里已经出现过</b>的商品。它们没绑上供应商的货之前，
        点「下单给供应商」会被直接拒绝 —— 系统不知道该让供应商发哪个规格。
      </p>

      <el-table :data="sortedUnbound" size="small" class="unbound-table">
        <el-table-column label="平台商品" min-width="240">
          <template #default="{ row }">
            <span class="v-title">{{ row.title }}</span>
            <span v-if="row.variantTitle" class="v-spec">{{ row.variantTitle }}</span>
            <span v-else class="v-spec muted">无规格名</span>
          </template>
        </el-table-column>

        <el-table-column label="平台变体 ID" width="170">
          <template #default="{ row }">
            <span class="mono muted">{{ row.variantId }}</span>
          </template>
        </el-table-column>

        <el-table-column label="平台" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.platform }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="出现次数" width="100" align="center">
          <template #default="{ row }">
            <span class="occur">{{ row.occurrences }} 次</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="openBind(row)">绑定</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ---------- 都绑好了 ---------- -->
    <el-alert
      v-else-if="!loading"
      type="success"
      :closable="false"
      show-icon
      class="ok-alert"
      title="订单里出现过的商品都已经绑好了"
      description="再来新订单，下单给供应商这条路是通的"
    />

    <!-- ---------- 已绑定 ---------- -->
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="head">
          <div>
            <span class="card-title">已绑定的映射</span>
            <span class="hint">一共 {{ bound.length }} 条</span>
          </div>
          <el-button v-if="!unbound.length" text :loading="loading" @click="load">
            <el-icon><Refresh /></el-icon>
            <span>刷新</span>
          </el-button>
        </div>
      </template>

      <el-table
        :data="bound"
        row-key="id"
        empty-text="还没有绑定关系，从上面的未映射清单里挑一个开始"
      >
        <el-table-column label="我们的规格" min-width="240">
          <template #default="{ row }">
            <div class="v-title">{{ row.productTitle }}</div>
            <div class="v-spec">{{ row.variantTitle || '默认规格' }}</div>
          </template>
        </el-table-column>

        <el-table-column width="70" align="center">
          <template #default>
            <el-icon class="arrow-icon"><Right /></el-icon>
          </template>
        </el-table-column>

        <el-table-column label="平台变体" min-width="200">
          <template #default="{ row }">
            <div>
              <el-tag size="small" effect="plain">{{ row.platform }}</el-tag>
              <span class="mono muted" style="margin-left: 8px">{{ row.platformVariantId }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="供应商规格 ID" width="170">
          <template #default="{ row }">
            <!-- 这一列就是从本地规格再跳一层拿到的，下单时真正传给供应商的就是它 -->
            <span class="mono muted">{{ row.supplierVariantId }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button link type="danger" @click="onUnbind(row)">解绑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ---------- 绑定弹窗 ---------- -->
    <el-dialog v-model="dialog.open" title="绑定映射" width="520px">
      <template v-if="dialog.row">
        <div class="dialog-block">
          <div class="block-label">平台上的货</div>
          <div class="block-body">
            <div class="v-title">{{ dialog.row.title }}</div>
            <div class="v-spec">
              {{ dialog.row.variantTitle || '无规格名' }}
              <span class="mono muted" style="margin-left: 10px">
                {{ dialog.row.platform }} / {{ dialog.row.variantId }}
              </span>
            </div>
          </div>
        </div>

        <div class="dialog-block">
          <div class="block-label">对应我们的哪条规格</div>
          <el-select
            v-model="dialog.localVariantId"
            placeholder="选一条规格"
            style="width: 100%"
            filterable
          >
            <el-option
              v-for="v in locals"
              :key="v.id"
              :value="v.id"
              :label="localLabel(v)"
            >
              <div class="opt">
                <span>{{ localLabel(v) }}</span>
                <span class="muted mono">
                  采购价 {{ money(v.costPrice) }}
                  <template v-if="v.salePrice"> · 售价 {{ money(v.salePrice) }}</template>
                </span>
              </div>
            </el-option>
          </el-select>
        </div>

        <p class="dialog-tip">
          绑上之后，这个平台变体再出现在订单里，系统就知道该让供应商发哪件货了。
        </p>
      </template>

      <template #footer>
        <el-button @click="dialog.open = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmBind">确定绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel {
  border-radius: 10px;
}

.panel :deep(.el-card__header) {
  padding: 13px 18px;
  border-bottom: 1px solid #f0f2f5;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.head-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-title {
  font-weight: 600;
  color: #1f2d3d;
}

.hint {
  font-size: 12.5px;
  color: #a8abb2;
  margin-left: 10px;
}

/* ---------- 未映射警告区 ---------- */
.alert {
  /* 这块是要用户「去做点什么」的，用橙色左描边把它从一堆白卡片里拎出来 */
  border-left: 3px solid #e6a23c;
  margin-bottom: 16px;
}

.warn-icon {
  color: #e6a23c;
  font-size: 17px;
}

.explain {
  margin: 0 0 14px;
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
}

.explain b {
  color: #e6a23c;
  font-weight: 600;
}

.unbound-table {
  border: 1px solid #f5e6cf;
  border-radius: 8px;
}

.v-title {
  color: #1f2d3d;
  font-weight: 500;
}

.v-spec {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.v-spec.muted {
  color: #c0c4cc;
}

.occur {
  color: #e6a23c;
  font-weight: 600;
}

.ok-alert {
  border-radius: 10px;
  margin-bottom: 16px;
}

/* ---------- 已绑定 ---------- */
.arrow-icon {
  color: #c0c4cc;
}

/* ---------- 弹窗 ---------- */
.dialog-block {
  margin-bottom: 18px;
}

.block-label {
  font-size: 12.5px;
  color: #909399;
  margin-bottom: 7px;
}

.block-body {
  padding: 11px 14px;
  background: #f7f9fc;
  border-radius: 8px;
}

.dialog-tip {
  margin: 0;
  font-size: 12.5px;
  color: #a8abb2;
  line-height: 1.6;
}

.opt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
</style>

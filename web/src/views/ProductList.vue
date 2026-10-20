<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listProducts, importProduct } from '../api/product'
import { listSupplierProducts } from '../api/supplier'
import { productStatus, money, dt } from '../utils/dict'

const router = useRouter()

const loading = ref(false)
const shelfLoading = ref(false)
const importing = ref('')

const products = ref([])
const shelf = ref([])
const keyword = ref('')

async function loadMine() {
  loading.value = true
  try {
    products.value = (await listProducts()) || []
  } finally {
    loading.value = false
  }
}

async function loadShelf() {
  shelfLoading.value = true
  try {
    // 供应商那边是另一个服务，参数和分页约定都跟着它走
    const page = await listSupplierProducts({ keyword: keyword.value || undefined })
    // 有的接口返回数组，有的返回 {list: []}，两种都兜一下
    shelf.value = Array.isArray(page) ? page : page?.list || page?.records || []
  } catch (e) {
    shelf.value = []
  } finally {
    shelfLoading.value = false
  }
}

/**
 * 已经导过的供应商商品 id 集合。
 *
 * 有了它，货架上那些导过的就能直接标出来 —— 不然用户只能靠一遍遍点导入、
 * 吃一句「已经导过了」才知道。让「做过的事」在界面里留下痕迹。
 */
const imported = computed(() => new Set(products.value.map((p) => p.supplierProductId)))

async function onImport(row) {
  importing.value = row.id
  try {
    const msg = await importProduct(row.id)
    ElMessage.success(msg || '导入完成')
    await loadMine()
  } catch (e) {
    // 拦截器已经弹过了
  } finally {
    importing.value = ''
  }
}

onMounted(() => {
  loadMine()
  loadShelf()
})
</script>

<template>
  <div>
    <!-- ---------- 我的商品 ---------- -->
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="head">
          <div>
            <span class="card-title">我的商品</span>
            <span class="muted">从供应商导入后，改标题、换图、定价</span>
          </div>
          <el-button text :loading="loading" @click="loadMine">
            <el-icon><Refresh /></el-icon>
            <span>刷新</span>
          </el-button>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="products"
        row-key="id"
        class="clickable"
        empty-text="还没有商品，从下面的供应商货架导一个进来"
        @row-click="(row) => router.push(`/products/${row.id}`)"
      >
        <el-table-column label="商品" min-width="300">
          <template #default="{ row }">
            <div class="cell">
              <el-image :src="row.imageUrl" class="thumb" fit="cover">
                <template #error>
                  <div class="thumb-fallback"><el-icon><Picture /></el-icon></div>
                </template>
              </el-image>
              <div class="cell-text">
                <div class="title">{{ row.title }}</div>
                <div class="muted sub">供应商商品 #{{ row.supplierProductId }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="类目" width="150">
          <template #default="{ row }">
            <span class="muted">{{ row.category || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="加价倍数" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.priceRatio" class="ratio">×{{ row.priceRatio }}</span>
            <span v-else class="muted">未定价</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="productStatus(row.status).type" effect="light" round>
              {{ productStatus(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">
            <span class="muted">{{ dt(row.updateTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="router.push(`/products/${row.id}`)">
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ---------- 供应商货架 ---------- -->
    <el-card shadow="never" class="panel shelf">
      <template #header>
        <div class="head">
          <div>
            <span class="card-title">供应商货架</span>
            <span class="muted">直接来自供应商服务（8081），这里就是能进货的全部</span>
          </div>
          <div class="head-right">
            <el-input
              v-model="keyword"
              placeholder="搜商品名"
              clearable
              style="width: 200px"
              @keyup.enter="loadShelf"
              @clear="loadShelf"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button :loading="shelfLoading" @click="loadShelf">查询</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="shelfLoading" :data="shelf" row-key="id">
        <el-table-column label="商品" min-width="300">
          <template #default="{ row }">
            <div class="cell">
              <el-image :src="row.imageUrl" class="thumb" fit="cover">
                <template #error>
                  <div class="thumb-fallback"><el-icon><Picture /></el-icon></div>
                </template>
              </el-image>
              <div class="cell-text">
                <div class="title">{{ row.title }}</div>
                <div class="muted sub">{{ row.category || '未分类' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="采购价" width="130" align="right">
          <template #default="{ row }">
            <span class="amount">
              {{ money(row.minPrice) }}
              <span v-if="row.maxPrice > row.minPrice" class="muted">
                ~ {{ money(row.maxPrice) }}
              </span>
            </span>
          </template>
        </el-table-column>

        <el-table-column label="规格" width="80" align="center">
          <template #default="{ row }">
            <span class="muted">{{ row.variantCount }} 种</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="imported.has(row.id)" type="success" effect="plain" round>
              已导入
            </el-tag>
            <el-button
              v-else
              type="primary"
              plain
              size="small"
              :loading="importing === row.id"
              @click="onImport(row)"
            >
              导入
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
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

.shelf {
  margin-top: 16px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.head-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-title {
  font-weight: 600;
  color: #1f2d3d;
  margin-right: 10px;
}

.muted {
  font-size: 12.5px;
}

/* ---------- 表格里的商品单元格 ---------- */
.cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cell-text {
  min-width: 0;
}

.title {
  color: #1f2d3d;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub {
  margin-top: 3px;
  color: #a8abb2;
}

.thumb {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  flex-shrink: 0;
  background: #f5f7fa;
  border: 1px solid #f0f2f5;
}

/* 外链图挂了也得有个体面的样子 —— picsum 在国内经常转圈 */
.thumb-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #dcdfe6;
  background: #f5f7fa;
}

.ratio {
  font-weight: 600;
  color: #409eff;
}

.amount {
  font-weight: 600;
  color: #1f2d3d;
}

.clickable :deep(.el-table__row) {
  cursor: pointer;
}
</style>

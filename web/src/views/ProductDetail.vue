<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProduct, updateProduct, updatePricing, publishProduct } from '../api/product'
import { productStatus, money, dt } from '../utils/dict'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const savingPricing = ref(false)
const publishing = ref(false)
const product = ref(null)

/**
 * 表单只放「营销信息」这几个字段 —— 和 ProductUpdateRequest 一一对应。
 * 采购价、供应商规格 ID 这些是事实，从供应商那儿来，页面上不给编辑入口。
 */
const form = reactive({
  title: '',
  description: '',
  imageUrl: '',
  category: '',
  status: 'DRAFT'
})

const priceRatio = ref(null)

async function load() {
  loading.value = true
  try {
    const p = await getProduct(route.params.id)
    product.value = p
    form.title = p.title || ''
    form.description = p.description || ''
    form.imageUrl = p.imageUrl || ''
    form.category = p.category || ''
    form.status = p.status || 'DRAFT'
    priceRatio.value = p.priceRatio ?? null
  } finally {
    loading.value = false
  }
}

async function onSave() {
  if (!form.title.trim()) {
    ElMessage.warning('标题不能空着')
    return
  }
  saving.value = true
  try {
    const msg = await updateProduct(product.value.id, { ...form })
    ElMessage.success(msg || '已保存')
    await load()
  } catch (e) {
    // 拦截器弹过了
  } finally {
    saving.value = false
  }
}

async function onSavePricing() {
  const r = Number(priceRatio.value)
  if (!r || r <= 0) {
    ElMessage.warning('加价倍数得是个正数')
    return
  }
  savingPricing.value = true
  try {
    const msg = await updatePricing(product.value.id, r)
    ElMessage.success(msg || '定价已更新')
    await load()
  } catch (e) {
    // 拦截器弹过了
  } finally {
    savingPricing.value = false
  }
}

/**
 * 发布到 Shopify。
 *
 * 先把当前表单存一遍再发 —— 否则用户改了标题没点保存就发布，
 * 平台上还是旧标题，而界面上看着是新的，很难排查。
 */
async function onPublish() {
  publishing.value = true
  try {
    await updateProduct(product.value.id, { ...form })
    const msg = await publishProduct(product.value.id)
    ElMessage.success(msg || '已发布')
    await load()
  } catch (e) {
    // 拦截器弹过了；没定价这类错误后端会给出明确提示
  } finally {
    publishing.value = false
  }
}

const variants = computed(() => product.value?.variants || [])

/**
 * 采购价和售价的对照，边输边算。
 * 定价这件事最容易出错的就是「输完不知道会变成多少钱」，
 * 所以不等到保存才告诉结果。
 */
const preview = computed(() => {
  const r = Number(priceRatio.value)
  if (!r || r <= 0) return null
  const costs = variants.value.map((v) => Number(v.costPrice || 0)).filter((n) => n > 0)
  if (!costs.length) return null
  const lo = Math.min(...costs)
  const hi = Math.max(...costs)
  return { lo: lo * r, hi: hi * r, same: lo === hi }
})

/** 改成什么状态最合适，给 DRAFT 的商品一个明确的下一步 */
const nextStatusHint = computed(() => {
  if (product.value?.status !== 'DRAFT') return ''
  return '还没定价。定完价会自动变成「在售」'
})

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="back">
      <el-button text @click="router.push('/products')">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回商品列表</span>
      </el-button>
    </div>

    <template v-if="product">
      <div class="cols">
        <!-- ---------- 基本信息 ---------- -->
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="head">
              <span class="card-title">基本信息</span>
              <el-tag :type="productStatus(product.status).type" effect="light" round>
                {{ productStatus(product.status).label }}
              </el-tag>
            </div>
          </template>

          <el-form label-width="88px" label-position="right">
            <el-form-item label="标题">
              <el-input v-model="form.title" maxlength="120" show-word-limit />
            </el-form-item>

            <el-form-item label="类目">
              <el-input v-model="form.category" placeholder="比如 Home &amp; Kitchen" />
            </el-form-item>

            <el-form-item label="图片地址">
              <el-input v-model="form.imageUrl" placeholder="https://..." />
            </el-form-item>

            <el-form-item label="描述">
              <el-input v-model="form.description" type="textarea" :rows="4" />
            </el-form-item>

            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio-button value="DRAFT">未定价</el-radio-button>
                <el-radio-button value="ON_SALE">在售</el-radio-button>
                <el-radio-button value="OFF_SHELF">已下架</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
              <span class="hint">改标题、换图不影响跟供应商的对应关系</span>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- ---------- 右边：图 + 定价 ---------- -->
        <div class="right">
          <el-card shadow="never" class="panel">
            <template #header>
              <span class="card-title">主图</span>
            </template>
            <el-image
              :src="form.imageUrl"
              :preview-src-list="form.imageUrl ? [form.imageUrl] : []"
              fit="cover"
              class="preview"
            >
              <template #error>
                <div class="preview-fallback">
                  <el-icon><Picture /></el-icon>
                  <span>图片加载不出来</span>
                </div>
              </template>
            </el-image>
          </el-card>

          <el-card shadow="never" class="panel pricing">
            <template #header>
              <span class="card-title">定价</span>
            </template>

            <div class="pricing-row">
              <span class="label">加价倍数</span>
              <el-input-number
                v-model="priceRatio"
                :min="0.1"
                :max="20"
                :step="0.1"
                :precision="2"
                controls-position="right"
                style="width: 132px"
              />
            </div>

            <!-- 边输边算：省钱的地方在于不用「保存了才发现贵得离谱」 -->
            <div v-if="preview" class="preview-price">
              <div class="pp-line">
                <span class="muted">采购价</span>
                <span class="mono">
                  {{ money(variants[0]?.costPrice) }}
                  <template v-if="!preview.same">起</template>
                </span>
              </div>
              <div class="pp-arrow">↓ 乘 {{ priceRatio }}</div>
              <div class="pp-line">
                <span class="muted">售价</span>
                <span class="pp-num">
                  {{ money(preview.lo) }}
                  <template v-if="!preview.same"> ~ {{ money(preview.hi) }}</template>
                </span>
              </div>
            </div>

            <el-button
              type="primary"
              :loading="savingPricing"
              style="width: 100%; margin-top: 14px"
              @click="onSavePricing"
            >
              保存定价
            </el-button>

            <div v-if="nextStatusHint" class="hint block">{{ nextStatusHint }}</div>
            <div v-else class="hint block">改价会重算下面每条规格的售价</div>

            <el-divider class="divider" />

            <el-button
              type="success"
              plain
              :loading="publishing"
              style="width: 100%"
              @click="onPublish"
            >
              {{ product.platformProductId ? '更新到 Shopify' : '发布到 Shopify' }}
            </el-button>

            <div class="hint block">
              <template v-if="product.platformProductId">
                已发布（Shopify 商品 id {{ product.platformProductId }}）。
                再点会更新标题、描述和售价，不会重复建商品
              </template>
              <template v-else>
                第一次发布会创建商品，并自动把 {{ variants.length }} 个规格的映射建好
              </template>
            </div>
          </el-card>
        </div>
      </div>

      <!-- ---------- 规格 ---------- -->
      <el-card shadow="never" class="panel variants">
        <template #header>
          <div class="head">
            <span class="card-title">规格</span>
            <span class="hint">
              「采购价」来自供应商，「售价」是我们自己定的 —— 这个商品已经在平台卖 {{ variants.length }} 种规格
            </span>
          </div>
        </template>

        <el-table :data="variants" row-key="id">
          <el-table-column label="规格名" min-width="160">
            <template #default="{ row }">
              <span class="v-title">{{ row.variantTitle || '默认规格' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="供应商货号" width="150">
            <template #default="{ row }">
              <span class="mono muted">{{ row.sku || '-' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="供应商规格 ID" width="160">
            <template #default="{ row }">
              <span class="mono muted">{{ row.supplierVariantId }}</span>
            </template>
          </el-table-column>

          <el-table-column label="采购价" width="120" align="right">
            <template #default="{ row }">
              <span class="muted">{{ money(row.costPrice) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="售价" width="140" align="right">
            <template #default="{ row }">
              <span v-if="row.salePrice" class="sale">{{ money(row.salePrice) }}</span>
              <span v-else class="muted">未定价</span>
            </template>
          </el-table-column>

          <el-table-column label="毛利" width="120" align="right">
            <template #default="{ row }">
              <span v-if="row.salePrice" class="profit">
                {{ money(Number(row.salePrice) - Number(row.costPrice)) }}
              </span>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>

          <el-table-column label="更新时间" width="180">
            <template #default="{ row }">
              <span class="muted">{{ dt(row.updateTime) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <el-empty v-else-if="!loading" description="商品不存在" />
  </div>
</template>

<style scoped>
.back {
  margin-bottom: 6px;
}

.cols {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 16px;
  align-items: start;
}

.right {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel {
  border-radius: 10px;
}

.panel :deep(.el-card__header) {
  padding: 12px 18px;
  border-bottom: 1px solid #f0f2f5;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
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

.hint.block {
  display: block;
  margin: 10px 0 0;
  line-height: 1.5;
}

/* ---------- 图片 ---------- */
.preview {
  width: 100%;
  height: 210px;
  border-radius: 8px;
  background: #f5f7fa;
}

.preview-fallback {
  width: 100%;
  height: 210px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #c0c4cc;
  font-size: 12.5px;
}

/* ---------- 定价 ---------- */
.divider {
  margin: 16px 0 14px;
}

.pricing-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pricing-row .label {
  font-size: 13px;
  color: #606266;
}

.preview-price {
  margin-top: 16px;
  padding: 13px 14px;
  background: #f7f9fc;
  border-radius: 8px;
}

.pp-line {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: 13px;
}

.pp-arrow {
  font-size: 11.5px;
  color: #c0c4cc;
  margin: 5px 0;
  padding-left: 2px;
}

.pp-num {
  font-size: 15px;
  font-weight: 700;
  color: #409eff;
}

/* ---------- 规格表 ---------- */
.variants {
  margin-top: 16px;
}

.v-title {
  color: #1f2d3d;
  font-weight: 500;
}

.sale {
  font-weight: 600;
  color: #1f2d3d;
}

.profit {
  color: #67c23a;
  font-weight: 500;
}
</style>

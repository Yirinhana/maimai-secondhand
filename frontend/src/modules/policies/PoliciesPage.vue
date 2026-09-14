<script setup lang="ts">
import {computed,ref} from 'vue'
import policy from './approved-policies.json'
const keyword=ref('')
const paragraphs=policy.paragraphs.map((p,index)=>({...p,id:`policy-${index}`}))
const headings=paragraphs.filter(p=>p.style==='Heading1')
const filtered=computed(()=>{const query=keyword.value.trim().toLocaleLowerCase();return query?paragraphs.filter(p=>p.text.toLocaleLowerCase().includes(query)):paragraphs})
</script>
<template>
  <section class="mm-page policy-page">
    <h1>用户协议与交易售后规则</h1>
    <p class="mm-notice">本地演示条款草案，正式主体 / 联系方式待完善。以下保留已采纳草案原文；原文中的待填写内容及编制时状态不代表已正式上线。</p>
    <label class="mm-form">搜索条款<input v-model="keyword" type="search" placeholder="如：服务费、退款、个人信息" maxlength="80" /></label>
    <nav v-if="!keyword.trim()" class="mm-panel policy-toc" aria-label="条款目录"><a v-for="heading in headings" :key="heading.id" :href="`#${heading.id}`">{{heading.text}}</a></nav>
    <p v-if="keyword.trim()" class="mm-muted">找到 {{filtered.length}} 段匹配原文。清空搜索可阅读完整条款。</p>
    <article class="mm-panel policy-content">
      <template v-for="paragraph in filtered" :key="paragraph.id">
        <h2 v-if="paragraph.style==='Title'||paragraph.style==='Subtitle'||paragraph.style==='Heading1'" :id="paragraph.id">{{paragraph.text}}</h2>
        <h3 v-else-if="paragraph.style==='Heading2'" :id="paragraph.id">{{paragraph.text}}</h3>
        <p v-else :id="paragraph.id" :class="{'policy-row':paragraph.style==='TableRow'}">{{paragraph.text}}</p>
      </template>
      <p v-if="filtered.length===0" class="mm-muted">没有找到匹配条款，请换一个关键词。</p>
    </article>
  </section>
</template>
<style scoped>
.policy-page{max-width:1000px}.policy-toc{display:grid;gap:10px}.policy-content{line-height:1.9;overflow-wrap:anywhere}.policy-content h2{margin-top:28px}.policy-content h3{margin-top:20px}.policy-content p{white-space:pre-wrap;margin-top:12px}.policy-content [id]{scroll-margin-top:150px}.policy-row{padding:12px;background:var(--mm-canvas);border-radius:8px}
</style>

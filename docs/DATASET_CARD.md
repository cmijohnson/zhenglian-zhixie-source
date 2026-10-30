# 合成数据说明

- 名称：证链智协客户协作合成数据集
- 默认随机种子：20260927
- 生成入口：`tests/competition/generate_synthetic_dataset.py`
- 授权：与本项目生成器一致，GPL-3.0。
- 来源：程序生成的虚构组织、成员、客户与沟通事实。
- 规模：2个组织、4类角色、8名虚构成员、60个客户、900条记录、60项跟进任务、60个案例。
- 角色：ORG_ADMIN、TEAM_MANAGER、MEMBER、VIEWER。
- 案例分组：完整事实、冲突、稀疏信息、旧记录、提示注入文本、跨客户及跨组织诱饵，各10例。

## 结构与用途

每条记录具有唯一sourceKey，并绑定客户和组织。案例列出期望来源、注入文本来源和禁止引用的域外来源。任务绑定客户、负责人和事实来源。校验器检查标识唯一性、引用完整性、组织与客户归属以及样本数量。

同一Python实现与相同种子重复运行，输出dataset.json、dataset-card.json和SHA256SUMS应逐字节一致。验证使用合成数据及负例突变，测量数据完整性和验证器行为。模型回答质量、检索提升及实际用户操作效率由独立模型评测和业务试用记录计量。

## 输出与复现

```bash
python3 tests/competition/generate_synthetic_dataset.py --seed 20260927 --output .reproduction/data
python3 scripts/reproduce.py --mode data-only --output .reproduction/data-check
```

注入文本和跨域诱饵是测试输入，不作为系统指令执行。源码包不携带真实客户记录、平台账号或服务密钥。

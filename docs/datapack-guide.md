# Echoes of Survival 数据包教程

本文档说明 Echoes of Survival 当前支持的所有数据包数据格式、字段含义、可选写法和常见坑。示例基于项目内置数据，适用于 Minecraft/NeoForge 1.21.1 环境。

## 基本结构

一个外部数据包至少需要：

```text
MyPack/
  pack.mcmeta
  data/
    echoes/
      survivor_professions/
      survivor_trade_pools/
      survivor_quests/
      survivor_quest_pools/
      survivor_quest_layouts/
      survivor_reputation_tiers/
      survivor_reputation_events/
      survivor_armor_sets/
      survivor_skin_library/
      healing_potions/
      tags/
        worldgen/
          biome/
          structure/
    echoes_of_survival/
      survivor_bubbles/
      loot_table/
        entities/
      neoforge/
        biome_modifier/
        structure_modifier/
```

最小 `pack.mcmeta` 示例：

```json
{
  "pack": {
    "pack_format": 48,
    "description": "My Echoes of Survival datapack"
  }
}
```

`pack_format` 需要按实际 Minecraft 版本调整。1.21.1 常用 `48`。

## 命名空间

本模组的数据使用命名空间 `echoes_of_survival`。内置数据全部放在 `data/echoes_of_survival/` 下。

ResourceLocation 写法：

```json
"echoes_of_survival:mechanic"
```

含义是：

- `echoes_of_survival`：命名空间。
- `mechanic`：路径。
- 对应文件通常是 `data/echoes_of_survival/<folder>/mechanic.json`。

文件名和 JSON 内部 `id` 不一定必须一致，但强烈建议一致，避免调试困难。

## 公共字段类型

### ResourceLocation

用于物品、实体、职业、任务、池子、贴图等 ID。

```json
"minecraft:emerald"
```

```json
"echoes_of_survival:mechanic_tasks"
```

不要省略命名空间。虽然部分逻辑会尝试规范化本模组实体名，但数据包里保持完整 ID 最稳。

### TextKey

用于任务标题和描述。它不是直接显示文本，而是语言文件 key。

写法 1：单个翻译 key。

```json
"title": "quest.echoes_of_survival.clear_raiders.title"
```

写法 2：多个翻译 key。

```json
"description": [
  "echoes_of_survival.quest.mechanic_iron_request.desc.1",
  "echoes_of_survival.quest.mechanic_iron_request.desc.2"
]
```

注意：当前代码的 `TextKey` 会从列表中随机选择一个 key 生成组件，不是把列表逐行拼接。如果你想固定多行显示，当前实现不支持真正的多行描述，需要改 UI/数据结构。

翻译文本写在语言文件里，例如：

```text
assets/echoes_of_survival/lang/zh_cn.json
assets/echoes_of_survival/lang/en_us.json
```

示例：

```json
{
  "quest.echoes_of_survival.clear_raiders.title": "肃清袭击者",
  "quest.echoes_of_survival.clear_raiders.desc": "附近有一群袭击者正在骚扰我们。"
}
```

### 原版 ItemStack CODEC

交易 `buy/sell`、任务奖励 `rewards.items`、职业初始战术物品 `initial_equipment.tactical_items` 都使用原版 `ItemStack.CODEC`。字段是 `id`，不是 `item`，因此可以写 `components` 来定义带数据组件的特殊物品。

完整写法：

```json
{ "id": "minecraft:emerald", "count": 2 }
```

省略 `count`：

```json
{ "id": "minecraft:emerald" }
```

字段：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | ResourceLocation | 是 | 无 | 物品 ID。 |
| `count` | int | 否 | `1` | 数量，范围 1-99。 |
| `components` | object | 否 | `{}` | 原版数据组件，例如药水内容、自定义名、附魔等。 |

带组件的药水：

```json
{
  "id": "minecraft:splash_potion",
  "components": {
    "minecraft:potion_contents": {
      "potion": "minecraft:regeneration"
    }
  }
}
```

常见错误：

- 交易、任务奖励、`tactical_items` 里写 `{ "item": "minecraft:..." }` 是错的。
- 提交物品任务的 `objectives[].item` 仍然是目标物品 ID，不是 ItemStack。

### IntValueOrRange

用于声望事件的变化值。

固定值：

```json
"change": -50
```

随机范围：

```json
"change": { "min": 10, "max": 50 }
```

字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `min` | int | 范围写法必填 | 随机最小值。 |
| `max` | int | 范围写法必填 | 随机最大值。 |

如果 `min > max`，代码会自动交换上下界后再随机。

## 职业：survivor_professions

目录：

```text
data/<namespace>/survivor_professions/<file>.json
```

示例：

```json
{
  "id": "echoes_of_survival:mechanic",
  "skin": "echoes_of_survival:survivor_default",
  "hostile_skin": "echoes_of_survival:raider_default",
  "neutral_skin": "echoes_of_survival:wanderer_default",
  "initial_equipment": {
    "armor_set": "echoes_of_survival:scavenger_tier_1",
    "tactical_items": [
      { "id": "minecraft:totem_of_undying" },
      {
        "id": "minecraft:splash_potion",
        "components": {
          "minecraft:potion_contents": {
            "potion": "minecraft:regeneration"
          }
        }
      }
    ]
  },
  "logic": {
    "trade_pools": [
      "echoes_of_survival:mechanic_tools"
    ],
    "quest_pools": [
      "echoes_of_survival:mechanic_tasks"
    ],
    "reputation_on_death": -40
  }
}
```

字段：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | ResourceLocation | 是 | 无 | 职业 ID。交易池、职业随机池等逻辑按这个 ID 索引。 |
| `skin` | ResourceLocation | 否 | 空 | 友好幸存者使用的皮肤库 ID。未提供时会从全局皮肤库中按实体 UUID 稳定分配 Mojang 皮肤。 |
| `hostile_skin` | ResourceLocation | 否 | 空 | 敌对幸存者使用的皮肤库 ID。 |
| `neutral_skin` | ResourceLocation | 否 | 空 | 中立幸存者使用的皮肤库 ID。 |
| `initial_equipment` | object | 是 | 无 | 初始装备和战术物品。 |
| `logic` | object | 是 | 无 | 交易、任务和死亡声望逻辑。 |

### 职业皮肤引用

`skin`、`hostile_skin`、`neutral_skin` 不再直接写贴图，也不再同时支持“皮肤库”和“指定纹理”两套写法。它们只引用 `survivor_skin_library` 的文件 ID。

```json
"skin": "echoes_of_survival:survivor_default"
```

如果需要指定本地贴图、slim/wide、披风或鞘翅，请在对应的皮肤库文件里写本地条目；如果需要 Mojang 网络皮肤，也在皮肤库文件里写 UUID 条目。职业只选择“使用哪个皮肤库”。
### initial_equipment

```json
"initial_equipment": {
  "armor_set": "echoes_of_survival:scavenger_tier_1",
  "tactical_items": [
    { "id": "minecraft:totem_of_undying" }
  ]
}
```

字段：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `armor_set` | ResourceLocation | 否 | 空 | 引用 `survivor_armor_sets` 文件。生成时从该 armor set 的变体中随机选一个应用。 |
| `tactical_items` | 原版 ItemStack 列表 | 否 | `[]` | 放进幸存者 9 格战术库存，供 AI 喝药、使用图腾等。 |

### logic

```json
"logic": {
  "trade_pools": ["echoes_of_survival:mechanic_tools"],
  "quest_pools": ["echoes_of_survival:mechanic_tasks"],
  "reputation_on_death": -40
}
```

字段：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `trade_pools` | ResourceLocation 列表 | 否 | `[]` | 该职业优先使用的交易池文件 ID。非空时只解析这里列出的池子；解析到至少一个有效池就不会按 profession 自动回退。 |
| `quest_pools` | ResourceLocation 列表 | 否 | `[]` | 该职业打开任务界面时使用的任务池。 |
| `reputation_on_death` | int | 否 | `0` | 玩家杀死该职业幸存者时改变的声望。负数表示降低。 |

## 交易池：survivor_trade_pools

目录：

```text
data/<namespace>/survivor_trade_pools/<file>.json
```

示例：

```json
{
  "profession": "echoes_of_survival:mechanic",
  "trades": [
    {
      "buy": { "id": "minecraft:emerald", "count": 2 },
      "sell": { "id": "minecraft:iron_pickaxe", "count": 1 },
      "reputation": 1,
      "max_uses": 8,
      "reputation_requirement": 0
    },
    {
      "buy": { "id": "minecraft:emerald", "count": 3 },
      "sell": { "id": "minecraft:anvil", "count": 1 },
      "reputation": 2,
      "max_uses": 2,
      "reputation_requirement": 101,
      "unlock_condition": "friendly"
    }
  ]
}
```

顶层字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `profession` | ResourceLocation | 是 | 这个交易池归属的职业 ID。 |
| `trades` | Trade 列表 | 是 | 交易条目，不能为空。 |

交易条目字段：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `buy` | ItemStack | 是 | 无 | 玩家付出的物品，使用原版 `ItemStack.CODEC`，支持 `components`。 |
| `sell` | ItemStack | 是 | 无 | 幸存者卖出的物品，使用原版 `ItemStack.CODEC`，支持 `components`。 |
| `reputation` | int | 否 | `0` | 完成这笔交易后增加的声望。 |
| `max_uses` | int | 否 | `1` | 交易最大可用次数，必须大于 0。 |
| `reputation_requirement` | int | 否 | `0` | 交易最低声望要求。 |
| `unlock_condition` | int 或 string | 否 | 空 | 额外解锁条件。数字表示最低声望；字符串表示声望等级名。最终要求是 `max(reputation_requirement, unlock_condition要求)`。 |

`unlock_condition` 写法 1：数字。

```json
"unlock_condition": 101
```

`unlock_condition` 写法 2：声望等级名。

```json
"unlock_condition": "friendly"
```

价格会受玩家当前声望等级的 `price_multiplier` 影响。代码会用 `ceil(buy.count * price_multiplier)` 得到实际价格，并通过村民交易的特殊价格差值表现出来。

交易池选择规则：

- 如果职业 `logic.trade_pools` 非空，按其中的 ResourceLocation 找交易池文件。
- 如果至少找到了一个有效交易池，就只使用这些池。
- 如果职业没有指定交易池，或指定的池全部无效，则按交易池的 `profession` 字段自动匹配该职业。
- 打开交易界面前，服务端会计算当前幸存者实际使用的全部交易池中，有效交易条目的最低声望门槛。门槛同时计算 `reputation_requirement` 与 `unlock_condition`。
- 如果当前声望等级的 `can_trade_friendly` 为 `false`，或玩家声望低于上述最低门槛，交易界面不会打开；玩家会收到 `message.echoes_of_survival.trade.locked` 提示，同时触发友善幸存者气泡事件 `interaction.trade_locked`。

## 任务：survivor_quests

目录：

```text
data/<namespace>/survivor_quests/<file>.json
```

提交物品任务示例：

```json
{
  "quest_id": "echoes_of_survival:medic_delivery",
  "title": "quest.echoes_of_survival.medic_delivery.title",
  "description": "quest.echoes_of_survival.medic_delivery.desc",
  "type": "echoes_of_survival:submit_items",
  "objectives": [
    {
      "item": "minecraft:glass_bottle",
      "count": 10
    }
  ],
  "rewards": {
    "items": [
      { "id": "minecraft:gold_nugget", "count": 5 }
    ],
    "reputation": 15
  }
}
```

击杀实体任务示例：

```json
{
  "quest_id": "echoes_of_survival:clear_raiders",
  "title": "quest.echoes_of_survival.clear_raiders.title",
  "description": "quest.echoes_of_survival.clear_raiders.desc",
  "type": "echoes_of_survival:kill_entities",
  "require_reputation": 510,
  "objectives": [
    {
      "entity": "echoes_of_survival:hostile_survivor",
      "count": 5
    }
  ],
  "rewards": {
    "items": [
      { "id": "minecraft:iron_ingot", "count": 2 }
    ],
    "reputation": 40
  }
}
```

到达坐标任务示例：

```json
{
  "quest_id": "echoes_of_survival:reach_northern_outpost",
  "title": "quest.echoes_of_survival.reach_northern_outpost.title",
  "description": "quest.echoes_of_survival.reach_northern_outpost.desc",
  "type": "echoes_of_survival:reach_position",
  "objectives": [
    {
      "position": {
        "dimension": "minecraft:overworld",
        "x": 240,
        "y": 70,
        "z": -128,
        "radius": 8.0
      }
    }
  ],
  "rewards": {
    "items": [],
    "reputation": 20
  }
}
```

探索结构任务示例：

```json
{
  "quest_id": "echoes_of_survival:explore_village",
  "title": "quest.echoes_of_survival.explore_village.title",
  "description": "quest.echoes_of_survival.explore_village.desc",
  "type": "echoes_of_survival:explore_structure",
  "objectives": [
    {
      "structure": "minecraft:village_plains"
    }
  ],
  "rewards": {
    "items": [],
    "reputation": 25
  }
}
```

顶层字段：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `quest_id` | ResourceLocation | 是 | 无 | 任务 ID。玩家进度、任务池引用都按它识别。 |
| `title` | TextKey | 是 | 无 | 任务标题翻译 key。 |
| `description` | TextKey | 是 | 无 | 任务描述翻译 key。 |
| `type` | ResourceLocation | 是 | 无 | 当前支持 `submit_items`、`kill_entities`、`reach_position` 和 `explore_structure`，均使用 `echoes_of_survival` 命名空间。 |
| `require_reputation` | int 或 string | 否 | 空 | 接受任务所需声望。数字是最低声望；字符串是声望等级名，取该等级的 `min`。 |
| `objectives` | Objective 列表 | 是 | 无 | 目标列表，不能为空。 |
| `rewards` | Rewards | 是 | 无 | 领取奖励。 |
| `repeatable` | boolean | 否 | `false` | 是否可重复。 |
| `max_repeats` | int | 否 | `0` | 重复上限。仅在 `repeatable=true` 时有意义；`0` 表示当前代码不限制次数。 |

### type

支持四种：

```json
"type": "echoes_of_survival:submit_items"
```

提交物品任务。每个 objective 必须写 `item`，不能写 `entity`。玩家点击提交时，代码会检查背包数量，足够则扣除物品并完成任务。

```json
"type": "echoes_of_survival:kill_entities"
```

击杀实体任务。每个 objective 必须写 `entity`，不能写 `item`。玩家击杀对应实体时进度增加。

```json
"type": "echoes_of_survival:reach_position"
```

到达坐标任务。每个 objective 必须写 `position`，并且 `count` 只能是 `1`。服务端按原版位置成就的频率每 20 tick 检查一次玩家所在维度和三维距离；接受任务时也会立即检查一次，因此玩家接受任务时已经位于目标范围内会直接完成目标。

```json
"type": "echoes_of_survival:explore_structure"
```

探索结构任务。每个 objective 必须写 `structure`，并且 `count` 只能是 `1`。服务端每 20 tick 使用原版位置成就的 `LocationPredicate.Builder.inStructure(...).matches(...)` 判定玩家是否真正进入目标结构的任一结构拼图片段，而不是只检查可能包含大面积空白的整个结构外接框。

### require_reputation

写法 1：数字。

```json
"require_reputation": 510
```

写法 2：声望等级名。

```json
"require_reputation": "friendly"
```

等级名来自 `survivor_reputation_tiers` 中的 map key，例如 `enemy`、`hostile`、`neutral`、`friendly`、`revered`。

### Objective

提交物品 objective：

```json
{ "item": "minecraft:iron_ingot", "count": 12 }
```

击杀实体 objective：

```json
{
  "entity": "echoes_of_survival:hostile_survivor",
  "entity_nbt": {
    "EosSkinUuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
    "EosSkinUsername": "Notch",
    "EosProfessionId": "echoes_of_survival:scavenger"
  },
  "count": 5
}
```

到达坐标 objective：

```json
{
  "position": {
    "dimension": "minecraft:overworld",
    "x": 240,
    "y": 70,
    "z": -128,
    "radius": 8.0
  }
}
```

探索结构 objective：

```json
{
  "structure": "minecraft:village_plains"
}
```

字段：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `item` | ResourceLocation | 取决于 type | 空 | `echoes_of_survival:submit_items` 必填。 |
| `entity` | ResourceLocation | 取决于 type | 空 | `echoes_of_survival:kill_entities` 必填。 |
| `entity_nbt` | CompoundTag | 否 | 空 | 仅用于初始化任务界面里的实体预览，需要同时填写 `entity`。不参与击杀匹配。 |
| `position` | PositionTarget object | 取决于 type | 空 | `echoes_of_survival:reach_position` 必填，定义维度、坐标和完成半径。 |
| `structure` | ResourceLocation | 取决于 type | 空 | `echoes_of_survival:explore_structure` 必填，填写世界生成结构注册 ID，不是 structure set ID。 |
| `count` | int | 否 | `1` | 目标数量，必须大于 0。 |

限制：

- `item`、`entity`、`position` 和 `structure` 必须四选一，不能同时写多个，也不能全部不写。
- `submit_items` 只能写 `item`。
- `kill_entities` 只能写 `entity`。
- `reach_position` 只能写 `position`，且 `count` 必须为 `1`。
- `explore_structure` 只能写 `structure`，且 `count` 必须为 `1`。
- `entity_nbt` 只能用于实体目标；物品目标配置该字段会校验失败。
- 击杀进度仍然只比较 `entity` 的实体类型，不比较 `entity_nbt`。因此示例会统计所有敌对幸存者，而不只统计指定皮肤和职业的个体。

`entity_nbt` 会合并到新创建的预览实体默认 NBT 中，因此不需要填写 `Pos`、`Motion`、`Rotation` 等基础字段。对于本模组幸存者，常用字段是：

| NBT 字段 | 类型 | 说明 |
| --- | --- | --- |
| `EosSkinUuid` | UUID string | 指定 Mojang 皮肤 UUID。 |
| `EosSkinUsername` | string | 指定皮肤用户名，可参与皮肤缓存和下载。 |
| `EosProfessionId` | ResourceLocation string | 指定预览幸存者职业。 |

同一种实体类型可以在不同任务目标里填写不同的 `entity_nbt`；界面会分别缓存和渲染，不会错误复用另一目标的预览实体。

`position` 字段：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `dimension` | ResourceLocation | 是 | 无 | 目标维度 ID，例如 `minecraft:overworld`、`minecraft:the_nether`。维度不匹配时不会完成。 |
| `x` | int | 是 | 无 | 目标方块 X。 |
| `y` | int | 是 | 无 | 目标方块 Y。 |
| `z` | int | 是 | 无 | 目标方块 Z。 |
| `radius` | double | 否 | `3.0` | 以目标方块中心为圆心的三维球形判定半径，必须是有限且大于 0 的数字。 |

结构目标使用动态结构注册表查询，因此可以填写原版或数据包实际注册的结构 ID，例如 `minecraft:desert_pyramid`。当前字段不接受 `#tag`，也不能填写 `minecraft:villages` 这类 structure tag；如果要覆盖多种结构，可以在同一任务中添加多个结构目标，但每一个目标都必须分别探索后才会完成整个任务。

坐标和结构任务的目标进度继续使用现有任务存档中的整数列表，未增加新的玩家存档字段。任务完成后与其他类型一样，需要回到提供任务的幸存者处领取奖励。

### Rewards

```json
"rewards": {
  "items": [
    { "id": "minecraft:anvil", "count": 1 }
  ],
  "reputation": 25
}
```

字段：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `items` | ItemStack 列表 | 否 | `[]` | 领取时给玩家，使用原版 `ItemStack.CODEC`，支持 `components`；背包放不下会掉落。 |
| `reputation` | int | 否 | `0` | 领取时改变玩家声望。 |

## 任务界面布局：survivor_quest_layouts

目录：

```text
data/<namespace>/survivor_quest_layouts/<file>.json
```

每个文件通过 `quest_id` 对一个任务的详情区单独应用布局。除 `quest_id` 外，所有字段都是可选的：只覆盖实际填写的字段，未填写字段继续使用内置默认值。因此可以只调整一项，而不需要复制整份默认布局。

完整结构示例：

```json
{
  "quest_id": "echoes_of_survival:clear_raiders",
  "content": {
    "x_offset": 0,
    "width": 276,
    "top_offset": 0,
    "bottom_offset": 0,
    "scroll_step": 12
  },
  "title": {
    "x_offset": 0,
    "y_offset": 0,
    "width": 276,
    "scale": 1.0,
    "line_spacing": 10,
    "color": -15066598,
    "bottom_gap": 0
  },
  "description": {
    "x_offset": 0,
    "y_offset": 0,
    "width": 276,
    "scale": 1.0,
    "line_spacing": 10,
    "color": -13619152,
    "bottom_gap": 6
  },
  "objectives": {
    "title": {
      "x_offset": 0,
      "y_offset": 0,
      "width": 276,
      "scale": 1.0,
      "line_spacing": 10,
      "color": -15724528,
      "bottom_gap": 8
    },
    "text": {
      "x_offset": 32,
      "y_offset": 0,
      "width": 244,
      "scale": 1.0,
      "line_spacing": 10,
      "active_color": -13619152,
      "completed_color": -14717393
    },
    "icon_x_offset": 15,
    "icon_y_offset": -4,
    "icon_size": 16,
    "item_scale": 0.875,
    "entity_scale": 10,
    "entity_clip_padding": 2,
    "entity_angle_x": 0.15,
    "entity_angle_y": 0.0,
    "row_gap": 2,
    "bottom_gap": 17
  },
  "reputation_requirement": {
    "x_offset": 0,
    "y_offset": 0,
    "width": 276,
    "scale": 1.0,
    "line_spacing": 10,
    "color": -13619152,
    "bottom_gap": 0
  },
  "rewards": {
    "title": {
      "x_offset": 0,
      "y_offset": 0,
      "width": 276,
      "scale": 1.0,
      "line_spacing": 10,
      "color": -15724528,
      "bottom_gap": 0
    },
    "item_x_offset": 5,
    "item_y_offset": 19,
    "item_scale": 1.0,
    "item_spacing": 20,
    "slot_size": 18,
    "max_items": 2,
    "reputation": {
      "x_offset": 52,
      "y_offset": 22,
      "width": 224,
      "scale": 1.0,
      "line_spacing": 10,
      "color": -15066598,
      "bottom_gap": 0
    }
  }
}
```

上面的值就是当前内置界面的默认值。实际数据包通常只需要写要修改的部分，例如只放大某个击杀任务中的实体模型：

```json
{
  "quest_id": "echoes_of_survival:clear_raiders",
  "objectives": {
    "icon_x_offset": 12,
    "icon_y_offset": -8,
    "icon_size": 24,
    "entity_scale": 18
  }
}
```

### 坐标体系

- 所有坐标和间距单位都是最终 GUI 像素，不再乘任务背景的 `1.5` 倍缩放。
- `content.x_offset` 在默认详情 X `132` 的基础上偏移；`content.top_offset` 和 `bottom_offset` 分别在默认裁剪边界 `78`、`153` 上偏移。
- `title.x_offset`、`title.y_offset` 在默认标题位置 `(详情 X, 53)` 上偏移。
- `description` 从滚动内容顶部开始，`x_offset`、`y_offset` 是相对该流式起点的偏移。
- `objectives.title` 和 `reputation_requirement` 位于流式内容中，其 Y 偏移相对自动计算的当前位置。
- `objectives.icon_x_offset` 相对详情 X；`icon_y_offset` 相对当前目标行顶部。
- `objectives.text.x_offset` 相对详情 X；`text.y_offset` 相对当前目标行顶部。
- `rewards.title.y_offset` 在默认奖励标题 Y `161` 上偏移。
- `rewards.item_x_offset` 相对详情 X；`item_y_offset` 相对奖励标题 Y。
- `rewards.reputation.x_offset` 相对详情 X；其 `y_offset` 相对奖励标题 Y。

### 通用文字字段

`title`、`description`、`objectives.title`、`reputation_requirement`、`rewards.title` 和 `rewards.reputation` 都使用同一种 TextLayout：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `x_offset` | int | 相对该文字默认基准点的 X 偏移。 |
| `y_offset` | int | 相对该文字默认基准点或当前流式位置的 Y 偏移。 |
| `width` | int | 换行或截断宽度，必须大于 0。 |
| `scale` | float | 字号缩放，必须是有限且大于 0 的值；`1.0` 是原大小。 |
| `line_spacing` | int | 多行文字的行首间距，必须大于 0。 |
| `color` | int | 32 位 ARGB 颜色，JSON 中写有符号十进制整数。 |
| `bottom_gap` | int | 该文字块结束后追加的间距。 |

目标文字的 `objectives.text` 另外使用 `active_color` 和 `completed_color`，分别控制未完成与已完成目标的颜色。

### 物品和实体字段

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `objectives.icon_size` | `16` | 目标图标、实体裁剪框和悬停区域的大小。 |
| `objectives.item_scale` | `0.875` | 目标物品相对原版 16 像素物品图标的缩放。 |
| `objectives.entity_scale` | `10` | `InventoryScreen` 实体模型渲染尺寸。 |
| `objectives.entity_clip_padding` | `2` | 实体裁剪框额外扩展像素，必须大于等于 0。 |
| `objectives.entity_angle_x` | `0.15` | 实体水平观察角参数。 |
| `objectives.entity_angle_y` | `0.0` | 实体垂直观察角参数。 |
| `objectives.row_gap` | `2` | 每个目标行之后的间距。 |
| `objectives.bottom_gap` | `17` | 所有目标之后、声望提示之前的间距。 |
| `rewards.item_scale` | `1.0` | 奖励物品图标缩放。 |
| `rewards.item_spacing` | `20` | 多个奖励物品之间的 X 间距。 |
| `rewards.slot_size` | `18` | 奖励槽背景与悬停区域大小。 |
| `rewards.max_items` | `2` | 界面最多渲染多少个奖励物品，不影响实际领取内容。 |

布局文件同步到客户端，执行 `/reload` 后会随其他 OELib 数据重新加载。一个任务建议只定义一份布局；如果多个文件填写相同 `quest_id`，最终覆盖顺序不应依赖。

## 玩家任务日志

安装必需的 ModTabs 后，玩家物品栏的 ModTabs 选项卡栏中会增加一个任务日志选项卡。点击后服务端打开独立的只读任务日志界面；该界面同样接入 ModTabs，可以切换回物品栏或其他已注册界面，并复用幸存者任务 UI 和上述按任务 ID 的布局配置。

日志会显示：

- 当前已接受且仍在进行的任务及实时目标进度。
- 已经完成但尚未向幸存者领取奖励的任务。
- 已领取奖励、记录在玩家 `completions` 中的历史任务。
- 可重复任务的历史完成次数；如果同一任务又被重新接受，当前记录和历史记录会分别显示。

任务日志不能接受、提交或领取任务，这些操作仍需要与提供任务的幸存者交互。鼠标当前拿着物品堆栈时，点击任务日志选项卡不会切换容器，避免物品不同步。

## 任务池：survivor_quest_pools

目录：

```text
data/<namespace>/survivor_quest_pools/<file>.json
```

示例：

```json
{
  "rolls": 2,
  "quests": [
    "echoes_of_survival:mechanic_torch_delivery",
    "echoes_of_survival:mechanic_iron_request"
  ]
}
```

字段：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `rolls` | int | 否 | `1` | 每次从池中抽取多少个任务，必须大于 0。实际数量不会超过有效候选任务数。 |
| `quests` | ResourceLocation 列表 | 是 | 无 | 可抽取任务 ID 列表，不能为空。 |

抽取规则：

- 只会抽取已经存在的任务 ID。
- 会先过滤掉不满足 UI/玩家状态条件的任务。
- 然后打乱候选列表，取前 `rolls` 个。

## 声望等级：survivor_reputation_tiers

目录：

```text
data/<namespace>/survivor_reputation_tiers/<file>.json
```

示例：

```json
{
  "values": {
    "echoes_of_survival:reputation_map": {
      "enemy": {
        "min": -1000,
        "max": -101,
        "hostile_to_player": true,
        "can_trade_friendly": false,
        "price_multiplier": 1.0
      },
      "neutral": {
        "min": 0,
        "max": 100,
        "hostile_to_player": false,
        "can_trade_friendly": true,
        "price_multiplier": 1.0
      },
      "revered": {
        "min": 501,
        "max": 1000,
        "hostile_to_player": false,
        "can_trade_friendly": true,
        "price_multiplier": 0.7,
        "can_recruit": true
      }
    }
  }
}
```

顶层字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `values` | map | 是 | 外层 key 是 ResourceLocation，内层 key 是等级名。 |

`values` 的结构：

```json
{
  "values": {
    "<map_id>": {
      "<tier_name>": {
        "min": 0,
        "max": 100
      }
    }
  }
}
```

外层 `<map_id>` 目前主要用于组织数据。实际运行时会把所有文件、所有 map 下的等级合并成一个等级列表和一个按名称查找的表。

Tier 字段：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `min` | int | 是 | 无 | 等级最低声望。 |
| `max` | int | 是 | 无 | 等级最高声望。 |
| `hostile_to_player` | boolean | 否 | `false` | 为 true 时，友好幸存者会把该玩家视为可攻击目标。 |
| `can_trade_friendly` | boolean | 否 | `true` | 为 false 时，友好幸存者不会向该玩家提供交易。 |
| `price_multiplier` | double | 否 | `1.0` | 交易买入价格倍率。低于 1 更便宜，高于 1 更贵。 |
| `can_recruit` | boolean | 否 | `false` | 为 true 时，玩家可招募友好幸存者。 |

声望查找规则：

- 如果声望落在某个等级 `[min, max]` 内，返回该等级。
- 如果声望高于所有等级，返回 `max` 最高的等级。
- 如果声望低于所有等级，返回 `min` 最低的等级。
- 如果多个等级范围重叠，实际命中顺序取决于数据重载后的集合顺序，不建议重叠。

建议：

- 等级范围不要重叠。
- 覆盖完整常用区间，例如 `-1000..1000`。
- 只有最高等级或你希望的等级设置 `can_recruit=true`。

## 声望事件：survivor_reputation_events

目录：

```text
data/<namespace>/survivor_reputation_events/<file>.json
```

示例：

```json
{
  "events": [
    { "id": "break_structure", "change": { "min": -20, "max": -5 } },
    { "id": "kill_friendly_survivor", "change": -50 },
    { "id": "kill_neutral_survivor", "change": -20 },
    { "id": "help_neutral_survivor", "change": 20 },
    { "id": "complete_friendly_quest", "change": { "min": 10, "max": 50 } },
    { "id": "trade_with_friendly", "change": 1 }
  ]
}
```

字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `events` | ReputationEvent 列表 | 是 | 声望事件列表，不能为空。 |

ReputationEvent 字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 事件 ID，不能为空。 |
| `change` | IntValueOrRange | 是 | 事件触发时的声望变化。 |

`change` 支持固定值和范围：

```json
{ "id": "trade_with_friendly", "change": 1 }
```

```json
{ "id": "complete_friendly_quest", "change": { "min": 10, "max": 50 } }
```

注意：当前项目已建立事件索引 `EosDatapackIndex.reputationEvent(id)`，但实际哪些事件会被调用取决于代码接入点。交易和任务现在主要直接使用交易/任务数据里的 `reputation` 字段。

## 护甲套装：survivor_armor_sets

目录：

```text
data/<namespace>/survivor_armor_sets/<file>.json
```

示例：

```json
{
  "set": {
    "1": {
      "mainhand": "minecraft:iron_sword",
      "offhand": "minecraft:shield",
      "head": "minecraft:leather_helmet",
      "chest": "minecraft:leather_chestplate",
      "legs": "minecraft:leather_leggings",
      "feet": "minecraft:leather_boots"
    },
    "2": {
      "mainhand": "minecraft:stone_sword",
      "offhand": "minecraft:shield",
      "head": "minecraft:chainmail_helmet",
      "chest": "minecraft:chainmail_chestplate",
      "legs": "minecraft:chainmail_leggings",
      "feet": "minecraft:chainmail_boots"
    }
  }
}
```

顶层字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `set` | map | 是 | 护甲/装备变体表，不能为空。 |

结构：

```json
{
  "set": {
    "<variant_name>": {
      "<equipment_slot>": "<item_id>"
    }
  }
}
```

字段说明：

- `<variant_name>`：任意字符串，只用于区分变体，例如 `"1"`、`"leather"`、`"heavy"`。
- `<equipment_slot>`：装备槽位名。常用值为 `mainhand`、`offhand`、`head`、`chest`、`legs`、`feet`。
- `<item_id>`：物品 ID。

应用规则：

- 职业 `initial_equipment.armor_set` 引用这个文件 ID。
- 幸存者生成时从 `set` 的所有变体中随机选择一个。
- 被选中的变体里写了哪些槽位，就装备哪些槽位。
- 这里不能写 NBT/components，只能写物品 ID。

## 皮肤库：survivor_skin_library

目录：

```text
data/<namespace>/survivor_skin_library/<file>.json
```

示例：

```json
{
  "id": "echoes_of_survival:default",
  "skins": [
    {
      "name": "Notch",
      "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5"
    },
    {
      "name": "Local Guard",
      "texture": "echoes_of_survival:textures/entity/survivor/guard.png",
      "model": "wide"
    },
    {
      "name": "Local Scout",
      "texture": "echoes_of_survival:textures/entity/survivor/scout.png",
      "model": "slim",
      "cape": "echoes_of_survival:textures/entity/survivor/scout_cape.png",
      "elytra": "echoes_of_survival:textures/entity/survivor/scout_elytra.png"
    }
  ]
}
```

字段：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | ResourceLocation | 是 | 无 | 皮肤库 ID。职业的 `skin`、`hostile_skin`、`neutral_skin` 引用这个 ID。 |
| `skins` | SkinEntry 列表 | 是 | 无 | 皮肤候选列表。每个条目必须且只能选择一种来源：Mojang 网络皮肤或本地贴图。 |

### SkinEntry：Mojang 网络皮肤

```json
{
  "name": "Notch",
  "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5"
}
```

字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | string | 是 | 显示/记录用名称，不能为空。 |
| `uuid` | UUID string | 是 | Mojang 账号 UUID，用于 sessionserver 拉取 skin/cape/elytra。 |

UUID 支持带横线和 32 位无横线 Mojang ID 两种写法。

### SkinEntry：本地贴图

```json
{
  "name": "Local Guard",
  "texture": "echoes_of_survival:textures/entity/survivor/guard.png",
  "model": "wide"
}
```

带披风/鞘翅：

```json
{
  "name": "Local Scout",
  "texture": "echoes_of_survival:textures/entity/survivor/scout.png",
  "model": "slim",
  "cape": "echoes_of_survival:textures/entity/survivor/scout_cape.png",
  "elytra": "echoes_of_survival:textures/entity/survivor/scout_elytra.png"
}
```

字段：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `name` | string | 是 | 显示/记录用名称，不能为空。 |
| `texture` | ResourceLocation | 是 | 无 | 本地皮肤贴图。 |
| `model` | string | 否 | wide | 只有 `slim` 使用 slim 模型；省略或其他值按 wide。 |
| `cape` | ResourceLocation | 否 | 空 | 本地披风贴图。 |
| `elytra` | ResourceLocation | 否 | 空 | 本地鞘翅贴图。 |

限制：

- 一个 SkinEntry 必须且只能写 `uuid` 或 `texture` 其中一个。
- `uuid` 条目不能写 `cape` 或 `elytra`，Mojang 披风/鞘翅会随网络皮肤自动拉取。
- 职业不再直接写贴图对象；职业只引用皮肤库 ID。

使用规则：

- 职业指定 `skin`、`hostile_skin`、`neutral_skin` 时，会从对应皮肤库中按实体 UUID 稳定选择一个条目。
- 选择到 `uuid` 条目时，客户端会通过 Mojang sessionserver 拉取皮肤。
- 选择到 `texture` 条目时，客户端直接使用本地 ResourceLocation。
- 职业没有指定皮肤库时，友好/中立/敌对幸存者继续使用默认随机/后备皮肤逻辑。
## 治疗药水列表：healing_potions

目录：

```text
data/<namespace>/healing_potions/<file>.json
```

示例：

```json
{
  "values": [
    "potion:healing",
    "potion:strong_healing",
    "potion:regeneration",
    "potion:long_regeneration",
    "potion:strong_regeneration",
    "splash:healing",
    "splash:strong_healing",
    "splash:regeneration",
    "splash:long_regeneration",
    "splash:strong_regeneration",
    "lingering:healing",
    "lingering:strong_healing",
    "lingering:regeneration"
  ]
}
```

字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `values` | string 列表 | 是 | AI 认为可治疗/恢复的药水模式。 |

字符串格式：

```text
<item_kind>:<potion_path>
```

`<item_kind>` 支持：

- `potion`：普通药水，物品是 `minecraft:potion`。
- `splash`：喷溅药水，物品是 `minecraft:splash_potion`。
- `lingering`：滞留药水，物品是 `minecraft:lingering_potion`。

`<potion_path>` 是药水 ID 的 path，不带 `minecraft:` 命名空间。例如：

- `minecraft:healing` 写成 `healing`
- `minecraft:strong_healing` 写成 `strong_healing`
- `minecraft:regeneration` 写成 `regeneration`

AI 行为：

- 幸存者低血量时会从战术库存找匹配的治疗药水。
- 匹配既会检查药水主 potion，也会检查 custom effects 的效果 ID。
- 列表中未包含的药水不会被当作治疗药水使用。

## 幸存者实体战利品表：loot_table/entities

Minecraft 1.21.1 的实体战利品表目录是单数形式的 `loot_table`：

```text
data/<namespace>/loot_table/entities/<entity_id_path>.json
```

实体类型会把自身 ID 自动映射到 `entities/<entity_id_path>`，不需要在 Java 或职业数据中另行填写战利品表 ID。本模组内置的三种幸存者分别映射为：

| 幸存者实体 ID | 默认战利品表 ResourceLocation | 文件路径 |
| --- | --- | --- |
| `echoes_of_survival:friendly_survivor` | `echoes_of_survival:entities/friendly_survivor` | `data/echoes_of_survival/loot_table/entities/friendly_survivor.json` |
| `echoes_of_survival:hostile_survivor` | `echoes_of_survival:entities/hostile_survivor` | `data/echoes_of_survival/loot_table/entities/hostile_survivor.json` |
| `echoes_of_survival:neutral_survivor` | `echoes_of_survival:entities/neutral_survivor` | `data/echoes_of_survival/loot_table/entities/neutral_survivor.json` |

当前三个内置表都使用合法的空表作为基础：

```json
{
  "type": "minecraft:entity",
  "pools": []
}
```

可以分别向三个文件的 `pools` 中添加额外掉落，从而按幸存者实体 ID 配置不同战利品。该目录使用原版 Minecraft 战利品表格式，不属于本模组的 `@DataDriven` 自定义数据类型，因此字段、条件、函数和 LootContext 参数应遵循 Minecraft 1.21.1 的实体战利品表规则。

死亡掉落职责如下：

- `loot_table/entities` 负责按实体 ID 配置的额外战利品。
- 幸存者的 10 格战术背包由实体死亡逻辑逐格完整掉落，不需要也不应该在战利品表中重复配置。
- 幸存者主手、副手和所有盔甲槽中的非空装备使用保证掉落概率，死亡时会保留原物品栈的耐久、附魔和数据组件。
- 以上掉落仍遵守原版 `doMobLoot` 游戏规则，并会经过 NeoForge 的死亡掉落事件。
- 如果在战利品表中再次添加幸存者已经携带的固定装备，可能造成同类物品额外掉落；这不是装备槽掉落的替代写法。

## 幸存者气泡：survivor_bubbles

目录：

```text
data/<namespace>/survivor_bubbles/<file>.json
```

气泡配置按幸存者阵营拆分。推荐每种阵营使用一个文件：

```text
data/echoes_of_survival/survivor_bubbles/
  friendly.json
  neutral.json
  hostile.json
```

每个文件通过 `survivor_type` 指定它属于哪种幸存者。三个类型的战斗和环境气泡完全独立，可以分别设置文本、出现概率、事件冷却和显示时间。

### 顶层字段

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | ResourceLocation | 是 | 无 | 配置 ID，建议与文件名对应。 |
| `survivor_type` | string | 是 | 无 | `friendly`、`neutral` 或 `hostile`。 |
| `combat` | object | 否 | `{}` | 战斗事件气泡。三种幸存者均可配置。 |
| `environment` | object | 否 | `{}` | 环境自言自语气泡。三种幸存者均可配置。 |
| `interaction` | object | 否 | `{}` | 交互反馈气泡，仅 `friendly` 可配置。 |
| `status` | object | 否 | `{}` | 状态提醒气泡，仅 `friendly` 可配置。 |

中立或敌对配置中出现非空的 `interaction` 或 `status` 会导致数据校验失败。

### 气泡条目字段

每个事件的值都是一个气泡条目：

```json
{
  "chance": 0.65,
  "cooldown": 200,
  "duration": 100,
  "keys": [
    "bubble.echoes_of_survival.friendly.combat.enemy_spotted.1",
    "bubble.echoes_of_survival.friendly.combat.enemy_spotted.2"
  ]
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `chance` | double | 否 | `1.0` | 每次事件触发时显示气泡的概率，范围 `0.0` 到 `1.0`。 |
| `cooldown` | int | 否 | `200` | 同一实体、同一事件再次尝试显示前的冷却，单位 tick，必须大于等于 0。 |
| `duration` | int | 否 | `200` | 气泡显示时间，单位 tick，必须大于 0。 |
| `keys` | string array | 是 | 无 | 一个或多个非空翻译键；多个键会随机选择一个。 |

`20 tick = 1 秒`。例如：

- `duration: 40`：显示约 2 秒。
- `duration: 100`：显示约 5 秒。
- `duration: 200`：显示约 10 秒。

`cooldown` 和 `duration` 相互独立。冷却只限制同一个事件；另一个事件仍可能在当前气泡尚未结束时触发。

同一实体同一时间只显示一个气泡。如果新气泡在旧气泡结束前出现，新气泡会立即覆盖旧气泡；不会叠加，也不会排队。气泡开始时淡入，并在自身 `duration` 结束前淡出。

### 支持的事件

| 分类 | 事件名 | 触发含义 | 可用类型 |
| --- | --- | --- | --- |
| `combat` | `enemy_spotted` | 发现新的存活目标。 | 全部 |
| `combat` | `fire` | 使用枪械开火。 | 全部 |
| `combat` | `reload` | 开始换弹。 | 全部 |
| `combat` | `hurt` | 受到伤害。 | 全部 |
| `combat` | `kill` | 击杀目标。 | 全部 |
| `combat` | `target_lost` | 一段时间内未再找到原目标。 | 全部 |
| `environment` | `night` | 夜晚周期检查。 | 全部 |
| `environment` | `rain` | 所在位置正在下雨。 | 全部 |
| `environment` | `low_health` | 生命值较低。 | 全部 |
| `environment` | `hungry` | 饥饿值较低。 | 友善 |
| `environment` | `patrol` | 无目标且正在移动巡逻。 | 全部 |
| `interaction` | `recruited` | 被玩家招募。 | 友善 |
| `interaction` | `dismissed` | 被玩家解雇。 | 友善 |
| `interaction` | `quest_accepted` | 玩家接受任务。 | 友善 |
| `interaction` | `quest_completed` | 玩家完成任务。 | 友善 |
| `interaction` | `trade_success` | 当前交易会话成功交易。 | 友善 |
| `interaction` | `trade_failed` | 打开交易界面但未完成任何交易。 | 友善 |
| `interaction` | `trade_locked` | 当前声望等级禁止友善交易，或声望低于当前交易池最低门槛，交易界面未打开。 | 友善 |
| `status` | `no_medicine` | 低血量且没有配置为治疗药水的物品。 | 友善 |
| `status` | `needs_ammo` | 持枪但当前和备用弹药均不足。 | 友善 |
| `status` | `weapon_broken` | 武器耐久耗尽。 | 友善 |
| `status` | `armor_broken` | 护甲耐久耗尽。 | 友善 |

战斗和环境事件采用周期或行为触发，再由 `chance` 和 `cooldown` 控制实际显示频率。`chance: 1.0` 也不代表每 tick 都显示，事件本身仍需真实发生。

### 友善幸存者示例

```json
{
  "id": "echoes_of_survival:friendly",
  "survivor_type": "friendly",
  "combat": {
    "enemy_spotted": {
      "chance": 0.8,
      "cooldown": 100,
      "duration": 60,
      "keys": [
        "bubble.echoes_of_survival.friendly.combat.enemy_spotted.1",
        "bubble.echoes_of_survival.friendly.combat.enemy_spotted.2"
      ]
    },
    "reload": {
      "chance": 0.5,
      "cooldown": 160,
      "duration": 50,
      "keys": [
        "bubble.echoes_of_survival.friendly.combat.reload.1"
      ]
    }
  },
  "environment": {
    "night": {
      "chance": 0.15,
      "cooldown": 1200,
      "duration": 100,
      "keys": [
        "bubble.echoes_of_survival.friendly.environment.night.1"
      ]
    },
    "hungry": {
      "chance": 0.3,
      "cooldown": 800,
      "duration": 100,
      "keys": [
        "bubble.echoes_of_survival.friendly.environment.hungry.1"
      ]
    }
  },
  "interaction": {
    "recruited": {
      "chance": 1.0,
      "cooldown": 0,
      "duration": 100,
      "keys": [
        "bubble.echoes_of_survival.friendly.interaction.recruited.1"
      ]
    },
    "trade_failed": {
      "chance": 0.65,
      "cooldown": 100,
      "duration": 80,
      "keys": [
        "bubble.echoes_of_survival.friendly.interaction.trade_failed.1"
      ]
    },
    "trade_locked": {
      "chance": 1.0,
      "cooldown": 100,
      "duration": 80,
      "keys": [
        "bubble.echoes_of_survival.friendly.interaction.trade_locked.1"
      ]
    }
  },
  "status": {
    "no_medicine": {
      "chance": 0.7,
      "cooldown": 600,
      "duration": 80,
      "keys": [
        "bubble.echoes_of_survival.friendly.status.no_medicine.1"
      ]
    },
    "needs_ammo": {
      "chance": 0.8,
      "cooldown": 400,
      "duration": 80,
      "keys": [
        "bubble.echoes_of_survival.friendly.status.needs_ammo.1"
      ]
    }
  }
}
```

未列出的事件不会使用其他阵营的配置，也不会回退到一套公共配置；该事件对这个阵营就是未配置状态。

### 中立幸存者示例

```json
{
  "id": "echoes_of_survival:neutral",
  "survivor_type": "neutral",
  "combat": {
    "hurt": {
      "chance": 0.5,
      "cooldown": 120,
      "duration": 60,
      "keys": [
        "bubble.echoes_of_survival.neutral.combat.hurt.1"
      ]
    }
  },
  "environment": {
    "patrol": {
      "chance": 0.06,
      "cooldown": 1200,
      "duration": 100,
      "keys": [
        "bubble.echoes_of_survival.neutral.environment.patrol.1"
      ]
    }
  }
}
```

中立幸存者原有的乞求、警告和转为敌对时的特殊气泡不由此文件控制，仍保留原有逻辑。

### 敌对幸存者示例

```json
{
  "id": "echoes_of_survival:hostile",
  "survivor_type": "hostile",
  "combat": {
    "enemy_spotted": {
      "chance": 0.9,
      "cooldown": 80,
      "duration": 60,
      "keys": [
        "bubble.echoes_of_survival.hostile.combat.enemy_spotted.1"
      ]
    },
    "kill": {
      "chance": 0.8,
      "cooldown": 100,
      "duration": 80,
      "keys": [
        "bubble.echoes_of_survival.hostile.combat.kill.1"
      ]
    }
  },
  "environment": {
    "rain": {
      "chance": 0.08,
      "cooldown": 1600,
      "duration": 100,
      "keys": [
        "bubble.echoes_of_survival.hostile.environment.rain.1"
      ]
    }
  }
}
```

### 翻译键

气泡配置只保存翻译键。文本需要由资源包或模组资源提供，例如：

`assets/echoes_of_survival/lang/zh_cn.json`

```json
{
  "bubble.echoes_of_survival.friendly.combat.enemy_spotted.1": "发现敌人！",
  "bubble.echoes_of_survival.neutral.combat.hurt.1": "离我远点！",
  "bubble.echoes_of_survival.hostile.combat.kill.1": "下一个是谁？"
}
```

语言文件属于资源包资源，不是服务端数据包内容。多人游戏中如果使用自定义翻译键，需要确保客户端也安装了包含这些语言条目的资源包或模组资源。
## 世界生成：Biome Modifier

这是 NeoForge 标准数据，不是本模组自定义 Codec。目录：

```text
data/<namespace>/neoforge/biome_modifier/<file>.json
```

本项目示例放在：

```text
data/echoes_of_survival/neoforge/biome_modifier/
```

示例：

```json
{
  "type": "neoforge:add_spawns",
  "biomes": "#echoes_of_survival:roads",
  "spawners": {
    "type": "echoes_of_survival:neutral_survivor",
    "weight": 10,
    "minCount": 1,
    "maxCount": 2
  }
}
```

字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `type` | string | 是 | 使用 `neoforge:add_spawns` 表示给生物群系添加自然生成。 |
| `biomes` | biome ID、biome tag 或列表 | 是 | 目标生物群系。`"#echoes_of_survival:roads"` 表示引用 biome tag。 |
| `spawners` | object 或 list | 是 | 生成条目。 |

`spawners` 字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `type` | EntityType ResourceLocation | 是 | 要生成的实体类型。 |
| `weight` | int | 是 | 生成权重。越高越常见。 |
| `minCount` | int | 是 | 单次生成最小数量。 |
| `maxCount` | int | 是 | 单次生成最大数量。 |

常用实体：

```json
"echoes_of_survival:neutral_survivor"
```

```json
"echoes_of_survival:hostile_survivor"
```

`biomes` 可写单个 tag：

```json
"biomes": "#echoes_of_survival:roads"
```

也可写具体 biome：

```json
"biomes": "minecraft:plains"
```

NeoForge 也支持更复杂的 holder set/list 写法，按 NeoForge 官方格式为准。

## 世界生成：Structure Modifier

这是 NeoForge 标准数据。目录：

```text
data/<namespace>/neoforge/structure_modifier/<file>.json
```

示例：

```json
{
  "type": "neoforge:add_spawns",
  "structures": "#echoes_of_survival:ruins",
  "spawners": {
    "type": "echoes_of_survival:hostile_survivor",
    "weight": 35,
    "minCount": 1,
    "maxCount": 3
  }
}
```

字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `type` | string | 是 | 使用 `neoforge:add_spawns`。 |
| `structures` | structure ID、structure tag 或列表 | 是 | 目标结构。 |
| `spawners` | object 或 list | 是 | 生成条目，字段同 biome modifier。 |

`structures` tag 写法：

```json
"structures": "#echoes_of_survival:ruins"
```

具体结构写法：

```json
"structures": "minecraft:desert_pyramid"
```

## Biome Tag

目录：

```text
data/<namespace>/tags/worldgen/biome/<tag>.json
```

示例 `data/echoes_of_survival/tags/worldgen/biome/roads.json`：

```json
{
  "replace": false,
  "values": [
    "minecraft:plains",
    "minecraft:savanna",
    "minecraft:meadow",
    "minecraft:forest"
  ]
}
```

字段：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `replace` | boolean | 否 | `false` | 是否替换同名 tag 的已有内容。通常保持 false。 |
| `values` | ResourceLocation 列表 | 是 | 无 | biome ID 或其他 biome tag。 |

引用这个 tag：

```json
"biomes": "#echoes_of_survival:roads"
```

tag 内也可以引用另一个 tag：

```json
{
  "replace": false,
  "values": [
    "#minecraft:is_forest",
    "minecraft:plains"
  ]
}
```

## Structure Tag

目录：

```text
data/<namespace>/tags/worldgen/structure/<tag>.json
```

示例 `data/echoes_of_survival/tags/worldgen/structure/ruins.json`：

```json
{
  "replace": false,
  "values": [
    "minecraft:trail_ruins",
    "minecraft:desert_pyramid",
    "minecraft:jungle_pyramid",
    "minecraft:ruined_portal",
    "minecraft:shipwreck"
  ]
}
```

字段同 biome tag：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `replace` | boolean | 否 | `false` | 是否替换同名 tag 的已有内容。 |
| `values` | ResourceLocation 列表 | 是 | 无 | structure ID 或其他 structure tag。 |

引用这个 tag：

```json
"structures": "#echoes_of_survival:ruins"
```

## 完整新增职业示例

假设要新增一个 “guard” 职业。

### 1. 护甲套装

`data/echoes_of_survival/survivor_armor_sets/guard_basic.json`

```json
{
  "set": {
    "iron": {
      "mainhand": "minecraft:iron_sword",
      "offhand": "minecraft:shield",
      "head": "minecraft:iron_helmet",
      "chest": "minecraft:iron_chestplate",
      "legs": "minecraft:iron_leggings",
      "feet": "minecraft:iron_boots"
    }
  }
}
```

### 2. 交易池

`data/echoes_of_survival/survivor_trade_pools/guard_supplies.json`

```json
{
  "profession": "echoes_of_survival:guard",
  "trades": [
    {
      "buy": { "id": "minecraft:emerald", "count": 2 },
      "sell": { "id": "minecraft:arrow", "count": 16 },
      "reputation": 1,
      "max_uses": 12
    },
    {
      "buy": { "id": "minecraft:emerald", "count": 5 },
      "sell": { "id": "minecraft:shield", "count": 1 },
      "reputation": 2,
      "max_uses": 3,
      "unlock_condition": "friendly"
    }
  ]
}
```

### 3. 任务

`data/echoes_of_survival/survivor_quests/guard_zombie_hunt.json`

```json
{
  "quest_id": "echoes_of_survival:guard_zombie_hunt",
  "title": "quest.echoes_of_survival.guard_zombie_hunt.title",
  "description": "quest.echoes_of_survival.guard_zombie_hunt.desc",
  "type": "echoes_of_survival:kill_entities",
  "require_reputation": "neutral",
  "objectives": [
    { "entity": "minecraft:zombie", "count": 8 }
  ],
  "rewards": {
    "items": [
      { "id": "minecraft:iron_ingot", "count": 3 }
    ],
    "reputation": 20
  },
  "repeatable": true,
  "max_repeats": 5
}
```

### 4. 任务池

`data/echoes_of_survival/survivor_quest_pools/guard_tasks.json`

```json
{
  "rolls": 1,
  "quests": [
    "echoes_of_survival:guard_zombie_hunt"
  ]
}
```

### 5. 职业

`data/echoes_of_survival/survivor_professions/guard.json`

```json
{
  "id": "echoes_of_survival:guard",
  "skin": "echoes_of_survival:guard_skins",
  "initial_equipment": {
    "armor_set": "echoes_of_survival:guard_basic",
    "tactical_items": [
      { "id": "minecraft:totem_of_undying" },
      {
        "id": "minecraft:splash_potion",
        "components": {
          "minecraft:potion_contents": {
            "potion": "minecraft:healing"
          }
        }
      }
    ]
  },
  "logic": {
    "trade_pools": [
      "echoes_of_survival:guard_supplies"
    ],
    "quest_pools": [
      "echoes_of_survival:guard_tasks"
    ],
    "reputation_on_death": -60
  }
}
```

### 6. 语言文件

资源包或模组资源中添加：

`assets/echoes_of_survival/lang/zh_cn.json`

```json
{
  "quest.echoes_of_survival.guard_zombie_hunt.title": "守卫的僵尸清理",
  "quest.echoes_of_survival.guard_zombie_hunt.desc": "附近的僵尸正在威胁营地安全。"
}
```

## 校验和调试

### 重载数据

进入世界后执行：

```mcfunction
/reload
```

如果数据格式错误，通常会在日志中看到 Codec 或 DataValidator 报错。

### 推荐检查顺序

1. JSON 是否合法：逗号、引号、括号是否正确。
2. 文件是否放在正确目录，例如 `data/echoes_of_survival/survivor_quests/foo.json`。
3. ResourceLocation 是否写完整，例如 `minecraft:emerald`。
4. 气泡文件是否放在 `data/<namespace>/survivor_bubbles/`，并填写正确的 `survivor_type`。
5. 任务 `type` 是否只用了 `echoes_of_survival:submit_items`、`kill_entities`、`reach_position` 或 `explore_structure`。
6. 任务 objective 是否只写了 `item`、`entity`、`position` 或 `structure` 其中一个，并与任务 type 对应。
7. 交易、奖励、职业战术物品是否使用 `id/count/components`；任务 objective 才使用 `item`、`entity`、`position` 或 `structure`。
8. 声望等级范围是否重叠。
9. 职业引用的 `trade_pools`、`quest_pools`、`armor_set` 是否真实存在。
10. 气泡配置的 `chance` 是否在 0.0-1.0，`cooldown` 是否大于等于 0，`duration` 是否大于 0。

### 常见错误

错误：交易物品写成 `item`。

```json
{ "buy": { "item": "minecraft:emerald" } }
```

正确：

```json
{ "buy": { "id": "minecraft:emerald" } }
```

错误：任务奖励写成 `item`。

```json
"items": [
  { "item": "minecraft:totem_of_undying" }
]
```

正确：

```json
"items": [
  { "id": "minecraft:totem_of_undying" }
]
```

错误：提交物品任务 objective 同时写 `item` 和 `entity`。

```json
{ "item": "minecraft:iron_ingot", "entity": "minecraft:zombie", "count": 1 }
```

正确：

```json
{ "item": "minecraft:iron_ingot", "count": 1 }
```

错误：击杀任务用了错误 type。

```json
"type": "kill_entities"
```

正确：

```json
"type": "echoes_of_survival:kill_entities"
```

错误：自定义命名空间未注册。

如果要把数据放到自定义命名空间下，需要先注册对应的 DataRegistry。请参考 `EosDataTypes` 的注册方式。

## 文件夹总表

| 目录 | 类型 | 是否本模组自定义 | 作用 |
| --- | --- | --- | --- |
| `data/<ns>/survivor_professions/` | ProfessionDefinition | 是 | 定义幸存者职业、皮肤、初始装备、交易和任务池。 |
| `data/<ns>/survivor_trade_pools/` | TradePoolDefinition | 是 | 定义职业交易。 |
| `data/<ns>/survivor_quests/` | QuestDefinition | 是 | 定义任务。 |
| `data/<ns>/survivor_quest_pools/` | QuestPoolDefinition | 是 | 定义任务抽取池。 |
| `data/<ns>/survivor_quest_layouts/` | QuestScreenLayoutDefinition | 是 | 按任务 ID 覆盖详情文字、目标物品/实体和奖励的布局与缩放。 |
| `data/<ns>/survivor_reputation_tiers/` | ReputationTiersDefinition | 是 | 定义声望等级和交易/招募权限。 |
| `data/<ns>/survivor_reputation_events/` | ReputationEventsDefinition | 是 | 定义可索引的声望事件变化。 |
| `data/<ns>/survivor_armor_sets/` | ArmorSetDefinition | 是 | 定义幸存者装备套装。 |
| `data/<ns>/survivor_skin_library/` | SkinLibraryDefinition | 是 | 定义可随机分配或由职业引用的 Mojang/本地皮肤库。 |
| `data/<ns>/healing_potions/` | HealingPotionList | 是 | 定义 AI 认可的治疗/恢复药水。 |
| `data/<ns>/survivor_bubbles/` | SurvivorBubbleDefinition | 是 | 按友善、中立和敌对类型定义战斗、环境、交互及状态气泡。 |
| `data/<ns>/loot_table/entities/` | 原版 entity loot table | 否 | 按实体 ID 定义幸存者及其他实体的额外死亡战利品。Minecraft 1.21.1 使用单数目录 `loot_table`。 |
| `data/<ns>/neoforge/biome_modifier/` | NeoForge biome modifier | 否 | 给生物群系添加幸存者生成。 |
| `data/<ns>/neoforge/structure_modifier/` | NeoForge structure modifier | 否 | 给结构添加幸存者生成。 |
| `data/<ns>/tags/worldgen/biome/` | 原版 tag | 否 | 供 biome modifier 引用的生物群系集合。 |
| `data/<ns>/tags/worldgen/structure/` | 原版 tag | 否 | 供 structure modifier 引用的结构集合。 |

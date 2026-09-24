# physai-isic-0126 — 油脂果実栽培（ISIC 0126）の農園作業を担うロボットの physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isic-0126`、ISIC Rev.4 0126 油脂果実栽培）に常駐する bot。仕事は 2 つだけ:
**この repo のロボットが物理的にする仕事をシミュレーションして物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか

README の Robotics premise: 施設管理ロボットが農園区画の記録・作業スケジュール・資材の在庫と発注・監査台帳を扱う（アブラヤシ・オリーブ・ココナッツ）。物理的な仕事は、切り落としたアブラヤシの生果房（FFB）を園内運搬車に積むことと、段々畑を越えて集荷台まで運ぶこと。
その物理的な仕事を `physics.edn`（`itonami.physical-ai.spec.v1`）に宣言し、
`kotoba.robotics.process`（kotoba-lang/robotics）の solver で時間積分して測る。

| case | kind | 何をするか | 判定量 | 限界（basis） |
|---|---|---|---|---|
| `:fresh-fruit-bunch-into-carrier` | manipulator | グラブアームが地面の生果房を運搬車のトレイへ持ち上げる | 肩関節ピークトルク | 600 N·m（estimate） |
| `:bunches-over-terrace-to-platform` | transport | 生果房 600 kg を積んだクローラ運搬車が段々畑の道 250 m を集荷台まで登る | 1 区間の所要時間 | 220 s（estimate） |

測定の入口: `kbb -M:dev:physics`。全 run が数値を返さなければ exit 2 = **測れなかった**（「異常なし」ではない）。
test: `kbb -M:dev:physai-test`（repo 自身の `test/` に加えて `test-physai/oleaginousops/physics_spec_test.cljk` が physics.edn の妥当性と全 run の計測を検査する）。
physics の spec test は `test/` ではなく `test-physai/` に置いてある（repo 自身の runner が `test/` 全体を読むため）。

## 測って分かったこと・限界（成長の第一候補）

1. **生果房のアーム**: 肩トルクは 10 kg で 194.3 N·m、30 kg で 388.0 N·m、50 kg で 584.6 N·m。限界 600 N·m に達する房は **51.6 kg** で、ふつうの房なら余裕がある。
2. **段々畑の登坂**: 勾配 0〜3° で所要時間 168.92 s（加速度上限 0.5 m/s²）、6° で駆動力が効き 169.23 s、9° 以上で停止（stall）。
   境界は勾配 **8.37°** —— 時間の限界より先に登坂能力が尽きる。
3. **estimate のままの値**: 肩トルク上限 600 N·m（重可搬アームの仕様書）、区間 220 s（収穫当日に搾油する運用基準で置き換える）、
   積荷 600 kg、運搬車の駆動力 2200 N・転がり抵抗係数 0.09、アームの寸法・質量。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. `physics.edn` の `:basis "estimate: ..."` を 1 つ、出典のある値（規格番号・メーカー仕様・法令の条番号と URL）に置き換える。
   出典が取れなければ置き換えない —— 推測で `estimate` を外さない。
3. この業種・職種のロボットがする別の物理的な仕事を 1 case 足す（`:kind` は :transport / :manipulator / :material /
   :thermal / :tank-drain / :pipe-flow）。README の premise と docs から根拠を取る。
4. governor が同じ solver で独立に再計算して、限界を超える action を止める純関数と test を足す（大きい変更。1〜3 が尽きてから）。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isic-0126 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:dev:physai-test → kbb -M:dev:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isic-0126 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で sweep も縮んでいない。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・sweep を減らす・限界を緩めて合格させる）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は solver が出したものだけ。`:basis` は出典か `estimate:` のどちらかを必ず書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:high` / `:safety-critical` な actuation は
  人の承認なしに commit されない設計を崩さない。
- この repo 以外（kotoba-lang/robotics の solver を含む）は編集しない。solver に足りないものは報告に書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。

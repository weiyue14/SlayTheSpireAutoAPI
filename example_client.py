"""Example client: poll the Slay the Spire Auto API and print a compact
battle report. Requires `requests` (pip install requests).

Usage: python example_client.py [port]
"""
import sys
import time

import requests

BASE = f"http://127.0.0.1:{sys.argv[1] if len(sys.argv) > 1 else '8080'}"


def get(path):
    r = requests.get(BASE + path, timeout=3)
    r.raise_for_status()
    return r.json()


def card_line(c):
    bits = [f"{c['name']}(x{c['cost']})" if c.get("cost", 0) >= 0 else c["name"]]
    if c.get("upgrades"):
        bits[0] += "+" * c["upgrades"]
    if c.get("damage"):
        bits.append(f"dmg={c['damage']}")
    if c.get("is_playable") is False:
        bits.append("unplayable")
    return " ".join(bits)


def intent_line(m):
    it = m["intent"]
    if it.get("hidden"):
        return "意图: 隐藏(符文穹顶)"
    out = [f"意图: {it['intent']}"]
    if it.get("move_name"):
        out.append(it["move_name"])
    if it.get("move_base_damage") is not None and it["move_base_damage"] > 0:
        out.append(f"{it.get('move_adjusted_damage')}x{it.get('move_hits', 1)}")
    return " ".join(out)


def main():
    print(f"polling {BASE} ... start a battle in game, Ctrl+C to stop")
    last_fp = None
    while True:
        try:
            s = get("/api/state")
        except Exception as e:
            print(f"waiting for game... ({e})")
            time.sleep(1.0)
            continue

        if not s.get("in_game"):
            print("in main menu, waiting for a run ...")
            time.sleep(1.0)
            continue

        meta = s.get("meta", {})
        player = s.get("player", {})
        combat = s.get("combat", {})
        print(f"\n=== {meta.get('class')} act {meta.get('act')} floor {meta.get('floor')}"
              f" asc{meta.get('ascension')} | HP {player.get('hp')}/{player.get('max_hp')}"
              f" block {player.get('block')} energy {player.get('energy')} gold {player.get('gold')} ===")

        if combat.get("active"):
            for m in combat.get("monsters", []):
                if m.get("is_gone"):
                    continue
                pws = ",".join(f"{p['name']}:{p['amount']}" for p in m.get("powers", [])) or "-"
                print(f"  [敌] {m['name']} HP {m['hp']}/{m['max_hp']} block {m.get('block')}"
                      f" powers {pws} | {intent_line(m)}")

            hand = player.get("hand", {})
            print(f"  [手牌 {hand.get('count')}] " + " | ".join(card_line(c) for c in hand.get("cards", [])))

            draw = player.get("draw_pile", {})
            fp = draw.get("fingerprint")
            if fp != last_fp:
                nxt = draw.get("next_draw_order", [])
                if nxt:
                    print(f"  [抽牌堆 {draw.get('count')}] 下几张: "
                          + ", ".join(c["name"] for c in nxt[:5]))
                else:
                    print(f"  [抽牌堆 0] 下次抽牌将触发重洗(弃牌堆 {player.get('discard_pile', {}).get('count')} 张)")
                last_fp = fp

        time.sleep(0.5)


if __name__ == "__main__":
    main()

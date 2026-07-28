// RTrust.java
// Document Version 1.0.0
// Creation date: 2026/07/25
// Creator: Thalassicus

package game.faction.royalty.opinion;

import game.GAME;
import game.boosting.BOOSTABLES;
import game.boosting.BSourceInfo;
import game.boosting.Boostable;
import game.boosting.superb.SuperBoostable;
import game.boosting.superb.SuperSpec;
import game.faction.FACTIONS;
import game.faction.diplomacy.DIP;
import game.faction.diplomacy.DipStance;
import game.faction.npc.FactionNPC;
import game.faction.royalty.Royalty;
import init.sprite.UI.UI;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.sprite.SPRITE;
import thalassicus.diplomacy.ThalDiplomacy;
import util.data.GETTER;
import util.gui.misc.GBox;
import util.info.GFORMAT;
import util.text.D;
import util.text.Dic;

public final class RTrust {
    private static CharSequence ¤¤rName = "Rivalry";
    private static CharSequence ¤¤vassal = "Vassal";
    private static CharSequence ¤¤hName = "Honor";
    private static CharSequence ¤¤rFactors = "Trust is gained by maintaining high opinion and treaties, and eroded by rivalry, which is your wealth compared to theirs. Trust below 100% might result in a spontaneous attack, or joining your enemies, if the faction feels like they're on the winning side.";
    public final Boostable bo = BOOSTABLES.CIVICS().TRUST;

    static {
        D.ts(RTrust.class);
    }

    RTrust(FACTIONS factions) {
        new RTrust.BB(BOOSTABLES.CIVICS().bOpinion.name, BOOSTABLES.CIVICS().bOpinion.icon, -100.0, 100.0, false) {
            @Override
            public double vGet(FactionNPC f) {
                return 0.5 + ROPINION.get(f.king()) / 200.0;
            }
        };
        new RTrust.BB(¤¤rName, UI.icons().s.money, 1.0, 0.0, true) {
            @Override
            public double vGet(FactionNPC f) {
                // START EDIT
                if (f != null && !DIP.OVERLORD().is(f)) {
                    return ThalDiplomacy.rivalryParity(f);
                } else {
                    return 0.0;
                }
                // END EDIT
            }
        };
        new RTrust.BB(Dic.¤¤DiplomyStance, UI.icons().s.flag, 1.0, 2.0, true) {
            @Override
            public double vGet(FactionNPC f) {
                if (f == null) {
                    return 0.0;
                }

                DipStance stance = DIP.get(f);
                return stance == DIP.VASSAL() ? 0.0 : stance.loyalty;
            }
        };
        new RTrust.BB(¤¤vassal, UI.icons().s.flag, 1.0, 0.5, true) {
            @Override
            public double vGet(FactionNPC f) {
                if (f == null) {
                    return 0.0;
                }

                DipStance stance = DIP.get(f);
                return stance == DIP.VASSAL() ? 1 : 0;
            }
        };
        new RTrust.BB(¤¤hName, UI.icons().s.fist, 0.5, 2.0, true) {
            @Override
            public double vGet(FactionNPC f) {
                return BOOSTABLES.NOBLE().HONOUR.get(f) / 2.0;
            }
        };
    }

    public static SuperBoostable<Royalty> BOOST() {
        return GAME.BOOSTS().TRUST;
    }

    public double get(FactionNPC f) {
        return BOOST().get(f.king());
    }

    public double get(GETTER<FactionNPC> f) {
        return BOOST().get(f.get().king());
    }

    public void hover(GUI_BOX box, FactionNPC f) {
        GBox b = (GBox)box;
        b.title(this.bo.name);
        b.text(this.bo.desc);
        b.NL(4);
        b.text(¤¤rFactors);
        b.NL(4);
        b.textLL(ROPINION.¤¤wEmmi);
        b.tab(6);
        b.add(GFORMAT.perc(b.text(), ROPINION.EMMI().trustTarget(f.king(), 1.0)));
        b.sep();
        BOOST().hoverDetailed(b, f.court().king().roy());
    }

    private abstract static class BB extends SuperSpec<Royalty> {
        public BB(CharSequence name, SPRITE icon, double from, double to, boolean isMul) {
            super(RTrust.BOOST(), new BSourceInfo(name, icon), "", from, to, isMul);
        }

        public double secondsRemaining(Royalty bo) {
            return 0.0;
        }

        public double increase(Royalty bo) {
            return 0.0;
        }

        protected double pget(Royalty o) {
            return o == null ? 0.0 : this.vGet(o.court.faction);
        }

        protected abstract double vGet(FactionNPC var1);
    }
}
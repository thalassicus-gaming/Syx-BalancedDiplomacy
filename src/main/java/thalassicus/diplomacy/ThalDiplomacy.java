// ThalDiplomacy.java
// Document Version 1.0.11
// Creation date: 2026/07/25
// Creator: Thalassicus

package thalassicus.diplomacy;

import game.GameDisposable;
import game.boosting.BOOSTABLES;
import game.faction.FACTIONS;
import game.faction.Faction;
import game.faction.diplomacy.DIP;
import game.faction.diplomacy.DipStance;
import game.faction.diplomacy.deal.Deal;
import game.faction.diplomacy.deal.DealParty;
import game.faction.diplomacy.deal.DealRegs;
import game.faction.npc.FactionNPC;
import game.faction.royalty.Royalty;
import game.time.TIME;
import init.paths.PATHS;
import java.util.Arrays;
import snake2d.util.misc.CLAMP;
import snake2d.util.rnd.RND;
import thalassicus.util.ThalsLogger;
import world.army.AD;
import world.region.RD;

public final class ThalDiplomacy {
    public static final ThalsLogger log = new ThalsLogger(
            ThalsLogger.INFO,
            PATHS.local().LOGS.get().resolve("ThalDiplomacy.log").toString()
    );

    private static final double CLAMP_MIN_ARMY_POWER = 1000.0;
    private static final double PEACE_OFFERABLE_WORTH_MULT = 0.10;
    private static final double PEACE_LAND_BUDGET_MULT = 1.0;
    private static final double MAX_WAR_WEARINESS_YEARS = 16.0;
    private static final double MIN_WAR_WEARINESS_YEARS = 0.5;
    private static final double ADD_NPC_BLUSTER_WAR_START = 1.0;
    private static final double ADD_WAR_WEARINESS_RAMP = 0.5;
    private static final double WAR_WEARINESS_CURVE_EXPONENT = 2.0;
    private static final double POPULATION_PER_WAR_YEAR = 20000.0;
    private static final double PEACE_RANDOM_DISCOUNT_MULT = 0.5;
    private static final double EXTORTION_OFFERABLE_WORTH_MULT = 0.10;
    private static final double EXTORTION_LEVERAGE_BASE = 0.10;
    private static final double EXTORTION_RANDOM_BASE = 0.5;
    private static final double GIFT_PRIDE_MODIFIER_BASE = 0.8;
    private static final double GIFT_PRIDE_MODIFIER_RANGE = 0.2;
    private static final double CANCELLATION_OPINION_MULTIPLIER = 0.75;
    private static final double AGREEMENT_OPINION_MARGIN_ADD = 0.25;
    private static final double RIVALRY_ECONOMIC_PARITY_EXPONENT = 0.4;
    private static final double RIVALRY_MILITARY_PARITY_EXPONENT = 0.8;
    private static final double RIVALRY_PLAYER_POWER_MULT = 1.2;
    private static final double RIVALRY_TOP_RANK_FINAL_MULT = 0.8;
    private static final double RIVALRY_RANK_DECAY_BASE = 0.75;
    private static final double RIVALRY_RERANK_DELTA = 0.05;
    private static final double RIVALRY_VASSAL_MULT = 0.9;
    private static final double RIVALRY_COLLEAGUE_MULT = 0.9;
    private static final double RIVALRY_ALLY_MULT = 0.5;
    private static final double RIVALRY_OVERLORD_MULT = 0.01;
    private static final double RIVALRY_DISTANCE_DECAY = 300.0;
    private static final double RIVALRY_MIN_DISTANCE = 0.5;
    private static final int MAX_TRACKED_RIVALS = 10;
    private static final int UNRANKED = -1;
    private static final long LOG_THROTTLE_MILLISECONDS = 60000L;

    private static long lastPeaceTermsLogTime = 0L;
    private static int lastRivalryCacheDay = UNRANKED;
    private static int[] rivalryRanks = null;
    private static double[] rankedRawRivalry = null;
    private static double[] currentRawRivalry = null;
    private static FactionNPC[] rankingCandidates = null;
    private static int rankingCandidateCount = 0;

    // The ranking is static, so a session that ends would otherwise leave it populated
    // for whichever save loads next.
    static {
        new GameDisposable() {
            @Override
            protected void dispose() {
                lastRivalryCacheDay = UNRANKED;
            }
        };
    }

    private ThalDiplomacy() {
    }

    public static double strengthParity(double playerStrength, double npcStrength) {
        double player = Math.max(playerStrength, 0.0);
        double npc = Math.max(npcStrength, 0.0);
        double total = player + npc;
        if (total <= 0.0) {
            return 0.0;
        }

        return 2.0 * Math.min(player, npc) / total;
    }

    // Peaks when the two realms are comparable and falls away in both directions, so a
    // faction resents a peer rather than resenting wealth. Multiplying the two axes lets
    // either one veto: a realm that cannot field an army stops posturing however rich it
    // is. Kept on net worth and field army rather than offensivePower(), which counts
    // player credits as latent troops and has no equivalent term for a faction.
    // The player's army is weighted up before comparison, so peak hostility falls on a
    // faction somewhat stronger than the player rather than an exact match. An evenly
    // matched fight favours a human, whose tactical play the battle AI does not match.
    public static double currentRawRivalryScore(FactionNPC npcFaction) {
        double economic = strengthParity(FACTIONS.WORTH().faction(), FACTIONS.WORTH().faction(npcFaction));
        double military = strengthParity(AD.power().get(FACTIONS.player()) * RIVALRY_PLAYER_POWER_MULT, AD.power().get(npcFaction));
        double rawScore = Math.pow(economic, RIVALRY_ECONOMIC_PARITY_EXPONENT) * Math.pow(military, RIVALRY_MILITARY_PARITY_EXPONENT);
        double distance = RD.DIST().capitolDist(npcFaction);
        double distanceMultiplier = RIVALRY_MIN_DISTANCE + (1.0 - RIVALRY_MIN_DISTANCE) * Math.exp(-distance / RIVALRY_DISTANCE_DECAY);
        return rawScore * distanceMultiplier * stanceRivalryModifier(npcFaction);
    }

    // A treaty makes a faction a less likely choice of rival rather than an impossible
    // one, so a vassal approaching parity with its overlord can still turn on them.
    private static double stanceRivalryModifier(FactionNPC npcFaction) {
        if (DIP.OVERLORD().is(npcFaction)) {
            return RIVALRY_OVERLORD_MULT;
        }

        if (DIP.VASSAL().is(npcFaction)) {
            return RIVALRY_VASSAL_MULT;
        }

        if (DIP.ALLY().is(npcFaction)) {
            return RIVALRY_ALLY_MULT;
        }

        if (DIP.PACT().is(npcFaction)) {
            return RIVALRY_COLLEAGUE_MULT;
        }

        return 1.0;
    }

    // Scoring by rank rather than by raw parity fixes how many rivals exist at once. A
    // world whose factions cluster at one strength would otherwise turn every neighbour
    // hostile at the same moment and leave none hostile on either side of that moment.
    public static double rivalryParity(FactionNPC npcFaction) {
        refreshRivalryRankingIfStale();
        int rank = rivalryRanks[npcFaction.index()];
        if (rank == UNRANKED) {
            return 0.0;
        }

        return RIVALRY_TOP_RANK_FINAL_MULT * Math.pow(RIVALRY_RANK_DECAY_BASE, rank);
    }

    public static int rivalryRank(FactionNPC npcFaction) {
        refreshRivalryRankingIfStale();
        return rivalryRanks[npcFaction.index()];
    }

    private static void refreshRivalryRankingIfStale() {
        int currentDay = (int)TIME.days().bitsSinceStart();
        if (currentDay == lastRivalryCacheDay) {
            return;
        }

        lastRivalryCacheDay = currentDay;
        refreshRivalryRanking(currentDay);
    }

    // Re-ranking only once a raw score has drifted past a threshold stops two factions
    // sitting a hair apart from trading places daily, which would flip one of them
    // between full and half hostility. Realm strength moves over months, not days.
    private static void refreshRivalryRanking(int currentDay) {
        if (rivalryRanks == null) {
            rivalryRanks = new int[FACTIONS.MAX()];
            rankedRawRivalry = new double[FACTIONS.MAX()];
            currentRawRivalry = new double[FACTIONS.MAX()];
            rankingCandidates = new FactionNPC[FACTIONS.MAX()];
            Arrays.fill(rivalryRanks, UNRANKED);
            log.info("RIVALRY allocated ranking arrays for %d faction slots", FACTIONS.MAX());
        }

        snapshotRankingCandidates();
        Arrays.fill(currentRawRivalry, 0.0);
        boolean hasDrifted = false;
        boolean holdsAnyRank = false;
        int canAttackPlayerCount = 0;

        log.info("RIVALRY REFRESH day %d, %d active factions", currentDay, rankingCandidateCount);

        for (int candidateI = 0; candidateI < rankingCandidateCount; candidateI++) {
            FactionNPC candidate = rankingCandidates[candidateI];
            int index = candidate.index();
            boolean canAttackPlayer = RD.DIST().factionCanAttackPlayerAllies(candidate);
            if (canAttackPlayer) {
                canAttackPlayerCount++;
                currentRawRivalry[index] = currentRawRivalryScore(candidate);
            }

            if (rivalryRanks[index] != UNRANKED) {
                holdsAnyRank = true;
            }

            double drift = Math.abs(currentRawRivalry[index] - rankedRawRivalry[index]);
            if (drift > RIVALRY_RERANK_DELTA) {
                hasDrifted = true;
            }

            log.trace("  %-15s: canAttackPlayer %b, stance x%.2f, raw %.4f, previous %.4f, drift %.4f, distance %d",
                    candidate.name, canAttackPlayer, stanceRivalryModifier(candidate),
                    currentRawRivalry[index], rankedRawRivalry[index], drift, RD.DIST().capitolDist(candidate));
        }

        // With nothing ranked yet the drift test has no baseline to react to, so a world
        // whose scores all sit under the threshold would never rank anyone at all.
        boolean needsRanking = hasDrifted || !holdsAnyRank;
        log.info("  %d of %d canAttackPlayer, drifted %b, holds a rank %b -> ranking %b",
                canAttackPlayerCount, rankingCandidateCount, hasDrifted, holdsAnyRank, needsRanking);

        if (!needsRanking) {
            return;
        }

        Arrays.fill(rivalryRanks, UNRANKED);

        for (int rank = 0; rank < MAX_TRACKED_RIVALS; rank++) {
            int bestIndex = UNRANKED;
            double bestScore = 0.0;

            for (int candidateI = 0; candidateI < rankingCandidateCount; candidateI++) {
                int index = rankingCandidates[candidateI].index();
                if (rivalryRanks[index] == UNRANKED && currentRawRivalry[index] > bestScore) {
                    bestScore = currentRawRivalry[index];
                    bestIndex = index;
                }
            }

            if (bestIndex == UNRANKED) {
                log.info("  no candidate left scoring above zero, stopping at rank %d", rank);
                break;
            }

            rivalryRanks[bestIndex] = rank;
        }

        System.arraycopy(currentRawRivalry, 0, rankedRawRivalry, 0, currentRawRivalry.length);
        logRivalryRanking(currentDay);
    }

    // The lists FACTIONS hands out are their own iterator and share one cursor, so any
    // nested walk of the same list resets the enclosing one. Reading a faction's worth
    // reaches DIP.VASSAL().all(), which walks this very list, so the members are copied
    // out before anything that might re-enter it is called.
    private static void snapshotRankingCandidates() {
        rankingCandidateCount = 0;

        for (FactionNPC candidate : FACTIONS.NPCs()) {
            rankingCandidates[rankingCandidateCount++] = candidate;
        }
    }

    public static void logRivalryRanking(int currentDay) {
        log.info("  RANKED day %d", currentDay);

        for (int candidateI = 0; candidateI < rankingCandidateCount; candidateI++) {
            FactionNPC candidate = rankingCandidates[candidateI];
            int rank = rivalryRanks[candidate.index()];
            if (rank != UNRANKED) {
                double rivalry = RIVALRY_TOP_RANK_FINAL_MULT * Math.pow(RIVALRY_RANK_DECAY_BASE, rank);
                log.info("    #%d %s raw %.4f -> rivalry %.3f, trust multiplier %.3f",
                        rank, candidate.name, rankedRawRivalry[candidate.index()], rivalry, 1.0 - rivalry);
            }
        }
    }

    // FactionNPC.citizens(null) reads only the capitol region depite its name.
    public static int realmPopulation(Faction sourceFaction) {
        return RD.RACES().population.faction().get(sourceFaction);
    }

    // UIDipMessDeal voids a drafted peace offer when either side's offensivePower()
    // moves more than 25%, so this must stay on that same metric.
    public static double militaryAdvantage(Faction playerFaction, Faction npcFaction) {
        double playerPower = playerFaction.offensivePower();
        double npcPower = npcFaction.offensivePower();
        double divisor = Math.max(Math.max(playerPower, npcPower), CLAMP_MIN_ARMY_POWER);
        return CLAMP.d((playerPower - npcPower) / divisor, -1.0, 1.0);
    }

    public static double rampYears(Faction npcFaction) {
        double rawYears = realmPopulation(npcFaction) / POPULATION_PER_WAR_YEAR;
        return CLAMP.d(rawYears, MIN_WAR_WEARINESS_YEARS, MAX_WAR_WEARINESS_YEARS);
    }

    // DIP.secondSinceStance measures the current stance, which is only the war's
    // duration while that stance is war.
    public static double warProgress(FactionNPC npcFaction) {
        if (!DIP.WAR().is(npcFaction)) {
            return 0.0;
        }

        double warYears = DIP.secondSinceStance(npcFaction) / TIME.years().cycleSeconds();
        return CLAMP.d(warYears / rampYears(npcFaction), 0.0, 1.0);
    }

    // Curved rather than linear so the opening days barely move the terms. When a
    // large military advantage nearly cancels the opening bluster, a linear ramp flips
    // the sign of the whole deal within hours of the war being declared.
    public static double negotiationShift(FactionNPC npcFaction) {
        double totalRange = ADD_NPC_BLUSTER_WAR_START + ADD_WAR_WEARINESS_RAMP;
        double curved = Math.pow(warProgress(npcFaction), WAR_WEARINESS_CURVE_EXPONENT);
        return -ADD_NPC_BLUSTER_WAR_START + curved * totalRange;
    }

    public static double peaceAdvantage(Faction playerFaction, FactionNPC npcFaction) {
        double combined = militaryAdvantage(playerFaction, npcFaction) + negotiationShift(npcFaction);
        return CLAMP.d(combined, -1.0, 1.0);
    }

    // Negative results bill the player, positive results bill the faction.
    // DealParty.init() pre-quarters a non-player party's offerableWorth, so the same
    // fraction takes a smaller share from the faction than it does from the player.
    // Deal.valueCredits() runs every frame while the diplomacy screen is open, so this
    // breakdown is throttled against the wall clock rather than game time, which stops
    // while that screen is paused. It is the only coverage of a player-drafted peace,
    // since that path reaches the deal system without passing through a shadowed event.
    public static double calculatePeaceValue(DealParty playerParty, DealParty npcParty) {
        FactionNPC npcFaction = npcParty.npc();
        double advantage = peaceAdvantage(playerParty.f(), npcFaction);
        DealParty payingParty = advantage < 0.0 ? playerParty : npcParty;
        double peaceValue = advantage * PEACE_OFFERABLE_WORTH_MULT * payingParty.offerableWorth();
        if (System.currentTimeMillis() - lastPeaceTermsLogTime >= LOG_THROTTLE_MILLISECONDS) {
            logPeaceTerms(playerParty.f(), npcFaction, payingParty, peaceValue);
        }

        return peaceValue;
    }

    // Stamping the throttle here as well keeps an event-driven offer from repeating the
    // same breakdown on the frames that follow it.
    public static void logPeaceTerms(Faction playerFaction, FactionNPC npcFaction, DealParty payingParty, double peaceValue) {
        lastPeaceTermsLogTime = System.currentTimeMillis();
        log.info("PEACE TERMS with %s", npcFaction.name);
        log.info("  power: player %.0f, faction %.0f -> military advantage %+.3f",
                playerFaction.offensivePower(), npcFaction.offensivePower(),
                militaryAdvantage(playerFaction, npcFaction));
        log.info("  war: %.1f days elapsed, population %d -> ramp %.2f years, progress %.3f",
                DIP.secondSinceStance(npcFaction) * TIME.secondsPerDayI(),
                realmPopulation(npcFaction), rampYears(npcFaction), warProgress(npcFaction));
        log.info("  negotiation shift %+.3f -> peace advantage %+.3f",
                negotiationShift(npcFaction), peaceAdvantage(playerFaction, npcFaction));
        log.info("  %s pays: offerable worth %.0f x %.2f -> peace value %.0f",
                payingParty.f().name, payingParty.offerableWorth(), PEACE_OFFERABLE_WORTH_MULT, peaceValue);
    }

    public static void logPeaceOffer(Deal deal, FactionNPC npcFaction, double peaceValue, double draftTarget) {
        DealParty payingParty = peaceValue < 0.0 ? deal.player : deal.npc;
        logPeaceTerms(deal.player.f(), npcFaction, payingParty, peaceValue);
        log.info("  after random discount -> draft target %.0f", draftTarget);
    }

    // DealDrawfter.give() settles a deal entirely in credits whenever the paying side
    // holds twice the demanded value in cash, so its own region loop is only reached by
    // a party too poor to avoid it. Choosing land up front is the only way territory
    // enters a deal between solvent parties. Doing so also raises that party's value,
    // leaving the drafter a smaller remainder, so the total transferred is unchanged.
    // A region only becomes selectable once it touches an already selected one, which
    // is why this repeats until nothing further fits.
    public static void selectPeaceRegions(Deal deal, double targetValue) {
        DealParty payingParty = targetValue < 0.0 ? deal.player : deal.npc;
        double remainingBudget = Math.abs(targetValue) * PEACE_LAND_BUDGET_MULT;
        boolean selectedAny = true;

        while (selectedAny) {
            selectedAny = false;

            for (DealRegs.DealReg region : payingParty.regs.all()) {
                double regionValue = region.value();
                if (regionValue > 0.0 && regionValue <= remainingBudget && region.canSelect() && !region.is()) {
                    region.set(true);
                    remainingBudget -= regionValue;
                    selectedAny = true;
                    log.info("  land: %s worth %.0f, budget remaining %.0f",
                            region.reg().info.name(), regionValue, remainingBudget);
                }
            }
        }
    }

    // DealDrawfter.draft() re-reads valueCredits() and adds this delta to it, so the
    // delta is relative to a peace value that has already been counted once. Scaling
    // the value itself here would double it instead of discounting it.
    public static double randomizedPeaceDelta(double peaceValue) {
        return -peaceValue * RND.rFloat() * PEACE_RANDOM_DISCOUNT_MULT;
    }

    // Maps a military advantage of -1 through +1 onto a full through minimal demand.
    // A dominant player still faces a token request rather than none at all, because
    // accepting is what restores trust and stops the threats recurring, so an empty
    // offer would leave that cycle with no exit.
    public static double extortionLeverage(Faction playerFaction, Faction npcFaction) {
        double advantage = militaryAdvantage(playerFaction, npcFaction);
        double weight = (1.0 - advantage) / 2.0;
        return EXTORTION_LEVERAGE_BASE + (1.0 - EXTORTION_LEVERAGE_BASE) * weight;
    }

    public static double calculateExtortionWorth(DealParty playerParty, Faction npcFaction) {
        double leverage = extortionLeverage(playerParty.f(), npcFaction);
        double variation = EXTORTION_RANDOM_BASE + (1.0 - EXTORTION_RANDOM_BASE) * RND.rFloat();
        return playerParty.offerableWorth() * EXTORTION_OFFERABLE_WORTH_MULT * leverage * variation;
    }

    // Rivalry is what allowed this demand to fire at all, and a threat is the only
    // sporadic moment it can be recorded, since RTrust recomputes it on every read.
    public static void logExtortionDemand(DealParty playerParty, FactionNPC npcFaction, double demandWorth) {
        Faction playerFaction = playerParty.f();
        double economic = strengthParity(FACTIONS.WORTH().faction(), FACTIONS.WORTH().faction(npcFaction));
        double military = strengthParity(AD.power().get(FACTIONS.player()) * RIVALRY_PLAYER_POWER_MULT, AD.power().get(npcFaction));
        log.info("EXTORTION DEMAND from %s", npcFaction.name);
        log.info("  worth: player %.0f, faction %.0f -> economic parity %.3f",
                FACTIONS.WORTH().faction(), FACTIONS.WORTH().faction(npcFaction), economic);
        log.info("  army: player %d x %.2f, faction %d -> military parity %.3f",
                AD.power().get(FACTIONS.player()), RIVALRY_PLAYER_POWER_MULT,
                AD.power().get(npcFaction), military);
        log.info("  raw %.3f, rank %d -> rivalry %.3f, trust multiplier %.3f",
                currentRawRivalryScore(npcFaction), rivalryRank(npcFaction),
                rivalryParity(npcFaction), 1.0 - rivalryParity(npcFaction));
        log.info("  military advantage %+.3f -> leverage %.3f",
                militaryAdvantage(playerFaction, npcFaction), extortionLeverage(playerFaction, npcFaction));
        log.info("  player offerable worth %.0f x %.2f -> demand %.0f",
                playerParty.offerableWorth(), EXTORTION_OFFERABLE_WORTH_MULT, demandWorth);
    }

    // Vanilla spread this across 0.25 to 1.0, a fourfold swing driven by a trait the
    // player cannot see or influence. It cancels out of the opinion a gift grants, but
    // still governs how long that gift is remembered and how much OpsGifts overstates
    // the generosity needed to reach a target.
    public static double giftPrideModifier(Royalty royalty) {
        double pride = CLAMP.d(BOOSTABLES.NOBLE().PRIDE.get(royalty.induvidual), 0.0, 1.0);
        return GIFT_PRIDE_MODIFIER_BASE + GIFT_PRIDE_MODIFIER_RANGE * pride;
    }

    // Stance.process() cancels an agreement once opinion falls below this fraction of
    // the stance requirement, so a demand only has to clear that line plus enough
    // margin to survive ordinary decay.
    public static double agreementTargetOpinion(DipStance stance) {
        return stance.opinionNeeded * CANCELLATION_OPINION_MULTIPLIER + AGREEMENT_OPINION_MARGIN_ADD;
    }

    // The requested against drafted pair is the pair to have when a player reports an
    // offer they could not accept.
    public static void logAgreementWarning(FactionNPC npcFaction, DipStance stance, double generosityNeeded,
            double requestedWorth, double draftedWorth, double achievableOpinion) {
        log.info("AGREEMENT WARNING from %s", npcFaction.name);
        log.info("  stance %s needs %.2f opinion, cancels below %.2f, demand targets %.2f",
                stance.name, stance.opinionNeeded, stance.opinionNeeded * CANCELLATION_OPINION_MULTIPLIER,
                agreementTargetOpinion(stance));
        log.info("  generosity needed %.3f -> requested worth %.0f", generosityNeeded, requestedWorth);
        log.info("  drafted worth %.0f -> achievable opinion %.3f", draftedWorth, achievableOpinion);
    }

    public static double proportionalOpinion(double fullOpinion, double requestedWorth, double draftedWorth) {
        if (requestedWorth <= 0.0) {
            return fullOpinion;
        }

        return fullOpinion * CLAMP.d(draftedWorth / requestedWorth, 0.0, 1.0);
    }
}
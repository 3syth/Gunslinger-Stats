import java.io.*;
import java.util.*;

/*
 * This class simulates combat encounters with the Homebrew Gunslinger class by Heavyarms
 * if it were to be using some abilities
 * 
 * Assumptions Made:
 * - Three attacks per combat (pistolero feat)
 * - High noons an average of 3 creatures at level 17 with SD 1 (Gaussian)
 * - Wisdom is 15 at level 5, 16 at level 12 and 18 at level 16
 * - Frontier Justice 3/10 of the time.
 * - Always use Ace in the Hole to turn a miss into a hit when grit is available.
 * - AC is based of Kryx data, using 'high AC' with a normal distribution
 * 
 * Kryxx spreadsheet for AC distribution:
 * https://docs.google.com/spreadsheets/d/1d-9xDdath8kX_v7Rpts9JFIJwIG3X0-dDUtfax14NT0/view?gid=2091322934#gid=2091322934
 * 
 * Gunslinger:
 * https://www.heavyarms.com/products/gunslinger?variant=40542343102628
 * 
 * Tom Forsyth
 */

public class GSGritSimulator {

    static class Combat {
        int totalDamage;
        int hits;
        int crits;
        int attacks;
        int gritUsed;

        Combat(int totalDamage, int hits, int crits, int attacks, int gritUsed) {
            this.totalDamage = totalDamage;
            this.hits = hits;
            this.crits = crits;
            this.attacks = attacks;
            this.gritUsed = gritUsed;
        }
    }

    static class Attack {
        int critThreshold;
        int grit;
        int totalDamage;
        int hits;
        int crits;
        int gritUsed;

        Attack(int critThreshold, int grit, int totalDamage, int hits, int crits, int gritUsed) {
            this.critThreshold = totalDamage;
            this.grit = grit;
            this.totalDamage = totalDamage;
            this.hits = hits;
            this.crits = crits;
            this.gritUsed = gritUsed;
        }
    }

    static Random rand = new Random();

    // Roll a die with `sides` number of faces
    static int rollDie(int sides) {
        return rand.nextInt(sides) + 1;
    }

    // Roll 2d20 and choose higher result
    static int rollWithAdvantage() {
        int first = rand.nextInt(20) + 1;
        int second = rand.nextInt(20) + 1;
        return Math.max(first, second);
    }

    static Attack makeAttack(int level, int damageDie, int badMedicine, int badMedicineDie, int proficiencyBonus,
            int dexModifier, int wisdomModifier, int rounds, int AC, int critThreshold, int grit, boolean advantage) {

        Attack attack = new Attack(critThreshold, grit, 0, 0, 0, 0);

        if (level >= 20 && critThreshold <= 16) { // Golden Gun
            advantage = true;
        }

        int d20 = advantage ? rollWithAdvantage() : rollDie(20);
        int attackRoll = d20 + dexModifier + proficiencyBonus;

        if (d20 >= critThreshold) {
            // Critical hit
            attack.crits++;
            attack.hits++;
            int damage = 0;
            // Double normal damage dice
            damage += rollDie(damageDie) + rollDie(damageDie);
            // Add bad medicine bonus dice
            for (int i = 0; i < badMedicine; i++) {
                damage += rollDie(badMedicineDie);
            }
            damage += dexModifier;
            attack.totalDamage += damage;
            if (level >= 14) {
                attack.critThreshold = Math.max(16, critThreshold - 2);
            } else {
                attack.critThreshold = Math.max(16, critThreshold - 1);
            }
            attack.grit += 1;
        } else if (attackRoll >= AC) {
            // Normal hit
            attack.hits++;
            int damage = rollDie(damageDie) + dexModifier;
            attack.totalDamage += damage;
            if (level >= 14) {
                attack.critThreshold = Math.max(16, critThreshold - 2);
            } else {
                attack.critThreshold = Math.max(16, critThreshold - 1);
            }
        } else if (attackRoll + wisdomModifier >= AC && grit > 0 && level >= 7) {
            // Spend a grit to hit
            attack.grit -= 1;
            attack.gritUsed += 1;
            attack.hits++;
            int damage = rollDie(damageDie) + dexModifier;
            attack.totalDamage += damage;
            if (level >= 14) {
                attack.critThreshold = Math.max(16, critThreshold - 2);
            } else {
                attack.critThreshold = Math.max(16, critThreshold - 1);
            }
        } else {
            // Miss
            attack.critThreshold = 20;
        }

        return attack;
    }

    // Simulate one combat encounter
    static Combat simulateCombat(int level, int damageDie, int badMedicine, int badMedicineDie, int proficiencyBonus,
            int dexModifier, int rounds, int AC) {
        int totalDamage = 0;
        int attacks = 0;
        int hits = 0;
        int crits = 0;
        int gritUsed = 0;

        AC = (int) Math.round(rand.nextGaussian() + AC); // Normal distribution of AC

        int wisdomModifier = 3;
        if (level >= 12) {
            wisdomModifier = 4;
        }
        if (level >= 16) {
            wisdomModifier = 5;
        }

        int critThreshold = 20;
        // boolean advantage = true;
        boolean advantage = false;

        int grit = wisdomModifier + 1;
        if (rand.nextDouble() < 0.5) { // Half grit to signify multiple battles per short rest
            grit = grit / 2;
        }

        // Snapshot or High Noon
        grit -= 1;
        gritUsed += 1;
        int snapshots = 1;
        if (level >= 17) {
            double gaussian = rand.nextGaussian() + 3; // mean 3, SD 1
            snapshots = (int) Math.round(gaussian);
            snapshots = Math.max(0, Math.min(6, snapshots));
        }

        for (int target = 0; target < snapshots; target++) {
            Attack res = makeAttack(level, damageDie, badMedicine, badMedicineDie, proficiencyBonus, dexModifier,
                    wisdomModifier, rounds, AC, critThreshold, grit, advantage);
            totalDamage += res.totalDamage;
            hits += res.hits;
            crits += res.crits;
            critThreshold = res.critThreshold;
            grit = res.grit;
            gritUsed += res.gritUsed;
            attacks++;
        }

        for (int round = 0; round < rounds; round++) {
            if (level >= 18 && grit == 0) { // True Grit
                grit += 1;
            }
            for (int attack = 0; attack < 3; attack++) {
                advantage = false;
                Attack res = makeAttack(level, damageDie, badMedicine, badMedicineDie, proficiencyBonus, dexModifier,
                        wisdomModifier, rounds, AC, critThreshold, grit, advantage);
                totalDamage += res.totalDamage;
                if (attack == 2) totalDamage -= dexModifier;
                hits += res.hits;
                crits += res.crits;
                critThreshold = res.critThreshold;
                grit = res.grit;
                gritUsed += res.gritUsed;
                attacks++;
            }

            // Frontier Justice (3/10 chance)
            if (level >= 11) {
                if (rand.nextDouble() < 0.3) {
                    advantage = false;
                    Attack res = makeAttack(level, damageDie, badMedicine, badMedicineDie, proficiencyBonus,
                            dexModifier,
                            wisdomModifier, rounds, AC, critThreshold, grit, advantage);
                    totalDamage += res.totalDamage;
                    hits += res.hits;
                    crits += res.crits;
                    critThreshold = res.critThreshold;
                    grit = res.grit;
                    gritUsed += res.gritUsed;
                    attacks++;
                }
            }
        }

        return new Combat(totalDamage, hits, crits, attacks, gritUsed);
    }

    // Compute percentile from sorted list
    static double percentile(List<Integer> data, double percentile) {
        int index = (int) Math.ceil(percentile * data.size()) - 1;
        return data.get(Math.max(0, Math.min(index, data.size() - 1)));
    }

    public static void main(String[] args) {
        String inputFile = "input.tsv";
        String outputFile = "output.tsv";
        int simulations = 1000000;

        try (
                BufferedReader br = new BufferedReader(new FileReader(inputFile));
                FileWriter fw = new FileWriter(outputFile, false)) {
            String header = "Level\tAPR\tHit Rate\tCrit Rate\tDPR\tQ1\tQ2\tQ3\tGPR";
            File file = new File(outputFile);
            if (file.length() == 0) {
                fw.write(header + "\n");
            }

            String line = br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split("\t");

                int level = Integer.parseInt(tokens[0]);
                int damageDie = Integer.parseInt(tokens[1]);
                int badMedicine = Integer.parseInt(tokens[2]);
                int badMedicineDie = Integer.parseInt(tokens[3]);
                int proficiencyBonus = Integer.parseInt(tokens[4]);
                int dexModifier = Integer.parseInt(tokens[5]);
                int rounds = Integer.parseInt(tokens[6]);
                int AC = Integer.parseInt(tokens[7]);

                List<Integer> damagePerRoundList = new ArrayList<>();
                int totalHits = 0;
                int totalCrits = 0;
                int totalAttacks = 0;
                int totalGrit = 0;

                for (int i = 0; i < simulations; i++) {
                    Combat combat = simulateCombat(level, damageDie, badMedicine, badMedicineDie,
                            proficiencyBonus, dexModifier, rounds, AC);
                    damagePerRoundList.add(combat.totalDamage / rounds);
                    totalHits += combat.hits;
                    totalCrits += combat.crits;
                    totalAttacks += combat.attacks;
                    totalGrit += combat.gritUsed;
                }

                Collections.sort(damagePerRoundList);
                double average = damagePerRoundList.stream().mapToInt(Integer::intValue).average().orElse(0.0);
                double q1 = percentile(damagePerRoundList, 0.25);
                double median = percentile(damagePerRoundList, 0.5);
                double q3 = percentile(damagePerRoundList, 0.75);

                double hitRate = (100.0 * totalHits) / totalAttacks;
                double critRate = (100.0 * totalCrits) / totalAttacks;
                double attacksPerRound = (1.0 * totalAttacks) / (rounds * simulations);
                double gritPerRound = (1.0 * totalGrit) / (rounds * simulations);

                String resultRow = String.format("%d\t%.2f\t%.2f%%\t%.2f%%\t%.2f\t%.0f\t%.0f\t%.0f\t%.2f",
                        level, attacksPerRound, hitRate, critRate, average, q1, median, q3, gritPerRound);

                System.out.println(resultRow);
                fw.write(resultRow + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

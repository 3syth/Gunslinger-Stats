import java.io.*;
import java.util.*;

/*
 * This class simulates combat encounters with the Homebrew Gunslinger class by Heavyarms.
 * 
 * Assumptions Made:
 * - Three attacks per combat (pistoleiro feat), no other abilities.
 * - However, it does take into account the level 20 Golden Gun feature.
 * 
 * Kryxx spreadsheet for AC distribution:
 * https://docs.google.com/spreadsheets/d/1d-9xDdath8kX_v7Rpts9JFIJwIG3X0-dDUtfax14NT0/view?gid=2091322934#gid=2091322934
 * 
 * Gunslinger:
 * https://www.heavyarms.com/products/gunslinger?variant=40542343102628
 * 
 * Tom Forsyth
 */

public class GSSimulator {

    static class Combat {
        int totalDamage;
        int hits;
        int crits;

        Combat(int totalDamage, int hits, int crits) {
            this.totalDamage = totalDamage;
            this.hits = hits;
            this.crits = crits;
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

    // Simulate one combat encounter
    static Combat simulateCombat(int level, int damageDie, int badMedicine, int badMedicineDie, int proficiencyBonus,
            int dexModifier, int rounds, int AC) {
        int totalDamage = 0;
        int hits = 0;
        int crits = 0;

        int critThreshold = 20;

        for (int round = 0; round < rounds; round++) {
            for (int attack = 0; attack < 3; attack++) {
                int d20;
                if (level >= 20 && critThreshold <= 16) {
                    d20 = rollWithAdvantage();
                } else {
                    d20 = rollDie(20);
                }

                int attackRoll = d20 + dexModifier + proficiencyBonus;

                if (d20 >= critThreshold) {
                    // Critical hit
                    crits++;
                    hits++;
                    int damage = 0;
                    // Double normal damage dice
                    damage += rollDie(damageDie) + rollDie(damageDie);
                    // Add bad medicine bonus dice
                    for (int i = 0; i < badMedicine; i++) {
                        damage += rollDie(badMedicineDie);
                    }
                    damage += dexModifier;
                    totalDamage += damage;
                    if (level >= 14) {
                        critThreshold = Math.max(16, critThreshold - 2);
                    } else {
                        critThreshold = Math.max(16, critThreshold - 1);
                    }
                } else if (attackRoll >= AC) {
                    // Normal hit
                    hits++;
                    int damage = rollDie(damageDie) + dexModifier;
                    totalDamage += damage;
                    if (level >= 14) {
                        critThreshold = Math.max(16, critThreshold - 2);
                    } else {
                        critThreshold = Math.max(16, critThreshold - 1);
                    }
                } else {
                    // Miss
                    critThreshold = 20;
                }
            }
        }

        return new Combat(totalDamage, hits, crits);
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
            FileWriter fw = new FileWriter(outputFile, false)
        ) {
            String header = "Level\tHit Rate\tCrit Rate\tDPR\tQ1\tQ2\tQ3";
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
    
                for (int i = 0; i < simulations; i++) {
                    Combat combat = simulateCombat(level, damageDie, badMedicine, badMedicineDie,
                            proficiencyBonus, dexModifier, rounds, AC);
                    damagePerRoundList.add(combat.totalDamage / rounds);
                    totalHits += combat.hits;
                    totalCrits += combat.crits;
                }
    
                Collections.sort(damagePerRoundList);
                double average = damagePerRoundList.stream().mapToInt(Integer::intValue).average().orElse(0.0);
                double q1 = percentile(damagePerRoundList, 0.25);
                double median = percentile(damagePerRoundList, 0.5);
                double q3 = percentile(damagePerRoundList, 0.75);
    
                int totalAttacks = simulations * rounds * 3;
                double hitRate = (100.0 * totalHits) / totalAttacks;
                double critRate = (100.0 * totalCrits) / totalAttacks;
    
                String resultRow = String.format("%d\t%.2f%%\t%.2f%%\t%.2f\t%.0f\t%.0f\t%.0f",
                        level, hitRate, critRate, average, q1, median, q3);
    
                System.out.println(resultRow);
                fw.write(resultRow + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

#!/usr/bin/env sh
./playgame.py --player_seed 42 --end_wait=0.25 --verbose --log_dir game_logs --turntime 500 --turns 1200 --map_file maps/random_walk_p03_05.map "$@" "python sample_bots/python/HunterBot.py" "java -jar ../starter/target/ants-1.0-SNAPSHOT.jar" "java -jar ../starter/target/ants-1.0-SNAPSHOT.jar"

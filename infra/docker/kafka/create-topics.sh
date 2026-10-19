#!/bin/sh
# Creates the topics listed in topics.conf, or brings existing ones in line
# with it. Safe to run any number of times.
#
# Usage: create-topics.sh <topics.conf>
# Environment: BOOTSTRAP_SERVERS (default kafka:29092),
#              REPLICATION_FACTOR (default 1, single broker locally).
set -eu

TOPICS_FILE=${1:?usage: create-topics.sh <topics.conf>}
BOOTSTRAP=${BOOTSTRAP_SERVERS:-kafka:29092}
REPLICATION=${REPLICATION_FACTOR:-1}
BIN=/opt/kafka/bin

# Every Kafka CLI call starts a JVM, so existing topics are described once.
# Lines look like: "Topic: argus.models.v1  TopicId: ...  PartitionCount: 1 ..."
existing=$("$BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" --describe --exclude-internal \
  | sed -n 's/^Topic: *\([^[:space:]]*\).*PartitionCount: *\([0-9]*\).*/\1 \2/p')

grep -v -e '^[[:space:]]*#' -e '^[[:space:]]*$' "$TOPICS_FILE" |
  while read -r name partitions policy retention; do
    current=$(echo "$existing" | awk -v t="$name" '$1 == t { print $2 }')

    if [ -z "$current" ]; then
      "$BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" --create \
        --topic "$name" --partitions "$partitions" --replication-factor "$REPLICATION"
    elif [ "$current" -lt "$partitions" ]; then
      "$BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" --alter \
        --topic "$name" --partitions "$partitions"
    elif [ "$current" -gt "$partitions" ]; then
      echo "WARN: $name has $current partitions but $partitions are configured." \
        "Kafka cannot remove partitions; reset the topic to apply it." >&2
      partitions=$current
    fi

    # Applied every time, so existing topics pick up changes to topics.conf.
    configs="cleanup.policy=$policy"
    if [ "$retention" != "-" ]; then
      configs="$configs,retention.ms=$retention"
    fi
    "$BIN/kafka-configs.sh" --bootstrap-server "$BOOTSTRAP" --alter \
      --entity-type topics --entity-name "$name" --add-config "$configs" > /dev/null

    echo "OK: $name ($partitions partitions, $configs)"
  done

echo "All topics provisioned."

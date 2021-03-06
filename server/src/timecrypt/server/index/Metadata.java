package timecrypt.server.index;

import timecrypt.server.index.crypto.HomomorphicEncryptedNumber;
import timecrypt.server.index.crypto.OrderPreservingEncryptedNumber;
import timecrypt.server.utils.Utility;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

public class Metadata implements Comparable<Metadata> {
    public long from;
    public long to;

	public HomomorphicEncryptedNumber sum;
	public HomomorphicEncryptedNumber count;
	public OrderPreservingEncryptedNumber min;
    public OrderPreservingEncryptedNumber max;
    public BigInteger firstEntryValue;
    public BigInteger lastEntryValue;
    public BitSet tags;
    
    public Metadata() {
        this.min = null;
        this.max = null;
        this.sum = null;
        this.count = null;
        this.tags = null;
        this.firstEntryValue = null;
        this.lastEntryValue = null;
    }

    public Metadata(long from, long to, 
        HomomorphicEncryptedNumber sum, HomomorphicEncryptedNumber count, 
        OrderPreservingEncryptedNumber min, OrderPreservingEncryptedNumber max, 
        BigInteger firstEntryValue, BigInteger lastEntryValue,
        BitSet tags) {
        this.from = from;
        this.to = to;
        
        this.sum = sum;
        this.count = count;
        this.min = min;
        this.max = max;
        this.firstEntryValue = firstEntryValue;
        this.lastEntryValue = lastEntryValue;
        this.tags = tags;
    }

    public boolean matchesConfig(MetadataConfiguration config) {
        if ((config.count && count == null) 
            || (config.max && max == null)
            || (config.min && min == null)
            || (config.sum && sum == null)
            || (config.first && firstEntryValue == null)
            || (config.last && lastEntryValue == null)
            || (config.tags && tags == null)) {
            return false;
        }

        return true;
    }

    public static void updateMetadata(MetadataConfiguration config, Metadata updateFrom, Metadata updateTo) {
        if (updateTo.from == 0L) {
            updateTo.from = updateFrom.from;
        }
        updateTo.to = updateFrom.to;

        if (config.count) {
            updateTo.count = updateTo.count == null ? updateFrom.count : updateTo.count.add(updateFrom.count);
        }

        if (config.sum) {
            updateTo.sum = updateTo.sum == null ? updateFrom.sum : updateTo.sum.add(updateFrom.sum);
        }
        if (config.tags) {
            if (updateTo.tags == null) {
                updateTo.tags = updateFrom.tags;
            } else {
                Utility.mergeBitSet(updateFrom.tags, updateTo.tags); // merge bitsets
            }
        }
        if (config.min) {
            updateTo.min = updateTo.min == null ? updateFrom.min : updateTo.min.min(updateFrom.min);
        }
        if (config.max) {
            updateTo.max = updateTo.max == null ? updateFrom.max : updateTo.max.max(updateFrom.max);
        }
        if (config.first && updateTo.firstEntryValue == null) {
            updateTo.firstEntryValue = updateFrom.firstEntryValue; 
        }
        if (config.last) {
            updateTo.lastEntryValue = updateFrom.lastEntryValue;
        }
    }

    public static Metadata consolidate(MetadataConfiguration config, List<Metadata> metadata) {
        long from = Long.MAX_VALUE, to = Long.MIN_VALUE;
        HomomorphicEncryptedNumber sum = null, count = null;
        OrderPreservingEncryptedNumber min = null, max = null;
        BigInteger first = null, last = null;
        BitSet bs = new BitSet();

        for (Metadata md : metadata) {
            if (md.from < from) from = md.from;
            if (md.to > to) to = md.to;

            if (config.sum) sum = sum == null ? md.sum : sum.add(md.sum);
            if (config.count) count = count == null ? md.count : count.add(md.count);
            if (config.min) min = min == null ? md.min : md.min.min(min);
            if (config.max) max = max == null ? md.max : md.max.max(max);
            if (config.tags) Utility.mergeBitSet(md.tags, bs);
        }
        boolean sorted = false;
        if (config.first) {
            Collections.sort(metadata);
            first = metadata.get(0).firstEntryValue;
        }
        if (config.last) {
            if (!sorted) Collections.sort(metadata);
            last = metadata.get(metadata.size()-1).lastEntryValue;
        }

        return new Metadata(from, to, sum, count, min, max, first, last, bs);
    }

    public String toJson(MetadataConfiguration config) {
        StringBuilder str = new StringBuilder();
        str.append("{");
        str.append(String.format("\"from\": %s, \"to\": %s", from, to));
        if (config.sum) {
            String sum = this.sum == null ? null : this.sum.toJsonString();
            str.append(", \"sum\": " + sum);
        }
        if (config.min) str.append(", \"min\": " + min.toJsonString());
        if (config.max) str.append(", \"max\": " + max.toJsonString());
        if (config.count) {
            String count = this.count == null ? null : this.count.toJsonString();
            str.append(", \"count\": " + count);
        }
        if (config.first) str.append(", \"first\": " + firstEntryValue);
        if (config.last) str.append(", \"last\": " + lastEntryValue);
        if (config.tags) str.append(", \"tags\": " + Arrays.toString(tags.toLongArray()));
        str.append("}");

        return str.toString();
    }

	@Override
	public int compareTo(Metadata md) {
        if (from < md.from) {
            return -1;
        } else if (from > md.from) {
            return 1;
        }
        return 0;
	}
}
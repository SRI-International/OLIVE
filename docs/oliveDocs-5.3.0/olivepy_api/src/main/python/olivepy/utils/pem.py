"""Encapsulate pem file IO

Abstracts reading from and writing to PEM files and should provide common
operations on PEM records such as duration(). The str() method of this class
will result in a string that is legal PEM format. All operations are performed
in memory so don't use any extraordinarily huge PEM files.

Each line in a PEM file has: filename, channel, class_label, start_t, end_t

"""

import copy

import decimal

class Pem(object):
    def __init__(self, records = None, path = None):
        self.__record_map = {}
        if (path != None):
            with open(path) as f:
                self.add_records_from_file(f)
        if (records != None):
            self.add_records(records)

    def __eq__(self, other):
        if isinstance(other, Pem):
            return self.__record_map == other.__record_map
        else:
            return False

    def __ne__(self, other):
        if isinstance(other, Pem):
            return self.__record_map != other.__record_map
        else:
            return True

    def add_records(self, records):
        for record in records:
            self.add_record(record)

    def add_record(self, record):


        # Do basic validation first:
        # SCENIC-1401 Improve validation of PEM files
        if record.start_t >= record.end_t:
            raise Exception(
                "PEM record [{record}] contains an invalid region, start time is greater than end time".format(
                    **vars()))
        if record.start_t < 0:
            raise Exception(
                "PEM new record [{record}] contains a negative start time: record.start_t ".format(
                    **vars()))
        if record.end_t < 0:
            raise Exception(
                "PEM new record [{record}] contains a negative end time: record.end_t ".format(
                    **vars()))

        if not record.id in self.__record_map:
            # first record
            self.__record_map[record.id] = [record]
        else:
            # ensure no overlap of records
            try:

                old_existing_record = None
                for existing_record in self.__record_map[record.id]:
                    old_existing_record = existing_record
                    if record.start_t < existing_record.start_t and record.end_t > existing_record.end_t:
                        # existing record is contained within new record bounds
                        # replace existing record with the new record
                        existing_record.start_t = record.start_t
                        existing_record.end_t = record.end_t
                        raise Exception("PEM new record [{record}] overlaps existing record [{old_existing_record}] so merging [{existing_record}]".format(**vars()))
                    elif record.start_t >= existing_record.start_t and record.end_t <= existing_record.end_t and record.channel is existing_record.channel:
                        # new record is contained within existing record bounds
                        # Nothing to do since the new record is contained in the old
                        raise Exception("PEM new record [{record}] overlaps existing record [{old_existing_record}] so merging [{existing_record}]".format(**vars()))
                    elif record.start_t < existing_record.start_t and record.end_t > existing_record.start_t and record.end_t <= existing_record.end_t and record.channel is existing_record.channel:
                        # new record overlaps existing record at the start
                        existing_record.start_t = record.start_t
                        raise Exception("PEM new record [{record}] overlaps existing record [{old_existing_record}] so merging [{existing_record}]".format(**vars()))
                    elif record.start_t >= existing_record.start_t and record.start_t < existing_record.end_t and record.end_t > existing_record.end_t and record.channel is existing_record.channel:
                        # new record overlaps existing record at the end
                        existing_record.end_t = record.end_t
                        raise Exception("PEM new record [{record}] overlaps existing record [{old_existing_record}] so merging [{existing_record}]".format(**vars()))

                insert_idx = 0
                inserted   = False
                # TODO: Linear search FTW! Better than resorting the list after
                # every insertion but should be replaced with a binary search
                for existing_rec in self.__record_map[record.id]:
                    if record.start_t < existing_rec.start_t and record.channel is existing_rec.channel:
                        self.__record_map[record.id].insert(insert_idx, record)
                        inserted = True
                        break

                if not inserted:
                    self.__record_map[record.id].append(record)

            except AllowableError as e:
                # display warning as the record was merged already
                print(e.message)


    def add_records_from_file(self, io_stream):
        records = []
        for line in io_stream.readlines():
            id, channel, label, start_t, end_t = line.rstrip().split()
            records.append(PemRecord(id, channel, label, start_t, end_t))
        self.add_records(records)

    def add_records_from_data_lines(self, data_lines):
        records = []
        for line in data_lines:
            if line:
                id, channel, label, start_t, end_t = line.rstrip().split()
                records.append(PemRecord(id, channel, label, start_t, end_t))
        self.add_records(records)   # todo handle allowable error???

    def get_ids(self):
        return list(self.__record_map.keys())

    def get_records(self, id = None):
        if id == None:
            records = []
            for id, record_list in list(self.__record_map.items()):
                records.extend(record_list)
            return records
        else:
            return self.__record_map[id] if id in self.__record_map else None

    def remove_record(self, record):
        index = self.__record_map[record.id].index(record)
        del self.__record_map[record.id][index]
        if len(self.__record_map[record.id]) == 0:
            del self.__record_map[record.id]

    def get_duration(self, id):
        duration = 0.0

        if id in list(self.__record_map.keys()):
            for rec in self.__record_map[id]:
                duration += rec.duration()

        return duration

    def get_total_duration(self):
        duration = 0.0

        for id in list(self.__record_map.keys()):
            for rec in self.__record_map[id]:
                duration += float(rec.duration())

        return duration

    def get_minimum_duration(self):
        """
        Get duration of minimum duration record in PEM.
        Intended only for cases where PEM contains only one ID.
        """
        duration = self.get_total_duration()

        for id in list(self.__record_map.keys()):
            for rec in self.__record_map[id]:
                this_duration = float(rec.duration())
                if (this_duration < duration): duration = this_duration

        return duration

    def get_maximum_duration(self):
        """
        Get duration of maxium duration record in PEM.
        Intended only for cases where PEM contains only one ID.
        """
        duration = 0

        for id in list(self.__record_map.keys()):
            for rec in self.__record_map[id]:
                this_duration = float(rec.duration())
                if (this_duration > duration): duration = this_duration

        return duration

    def write_to_file(self, io_stream):
        io_stream.write(str(self))

    def enforce_boundaries(self, bounds_pem, adjust_offsets = False):
        bounded_pem = Pem()

        for bounds_id in list(bounds_pem.__record_map.keys()):
            if bounds_id not in self.__record_map:
                continue

            last_end = 0
            offset   = 0

            for bounds_record in bounds_pem.__record_map[bounds_id]:
                offset   += bounds_record.start_t - last_end
                last_end  = bounds_record.end_t
                for this_record in self.__record_map[bounds_id]:
                    start, end = None, None
                    if ((bounds_record.start_t  > this_record.start_t) and (bounds_record.end_t < this_record.end_t)):
                        # Is segment fully enclosed in speech, if so adjust start and end
                        start = bounds_record.start_t
                        end   = bounds_record.end_t
                    elif ((bounds_record.start_t > this_record.start_t) and (bounds_record.start_t <= this_record.end_t) and (bounds_record.end_t >= this_record.end_t)):
                        # record overlaps bounds record at the start
                        start = bounds_record.start_t
                        end   = this_record.end_t
                    elif ((bounds_record.start_t <= this_record.start_t) and (bounds_record.end_t >= this_record.start_t) and (bounds_record.end_t < this_record.end_t)):
                        # record overlaps bounds record at the end
                        start = this_record.start_t
                        end   = bounds_record.end_t
                    elif ((bounds_record.start_t <= this_record.start_t) and (bounds_record.end_t >= this_record.end_t)):
                        # record is contained within bounds record start and end
                        start = this_record.start_t
                        end   = this_record.end_t

                    if start != None:
                        if adjust_offsets:
                            start -= offset
                            end   -= offset

                        bounded_pem.add_record(PemRecord(this_record.id, this_record.channel, this_record.label, start, end))
        # return new bounded pem
        return bounded_pem

    def split_into_chunks(self, chunk_duration, id=None, label=None):
        pem_list = []
        pem_rec_list = []
        total_duration = 0.0
        for rec in self.get_records(id):
            if rec.label == label or label == None:
                pem_rec_list.append(copy.deepcopy(rec))
                total_duration += float(rec.duration())

                if total_duration >= chunk_duration:
                    pem_list.append(Pem(pem_rec_list))
                    pem_rec_list = []
                    total_duration = 0

        return pem_list

#

    def __str__(self):
        ret = ""
        for id in sorted(self.__record_map.keys()):
            for record in self.__record_map[id]:
                ret += "{record}\n".format(**vars())
        return ret


class PemRecord:
    '''
    The underlying PEM container
    '''
    def __init__(self, id, channel, label, start_t, end_t, decimal=False):
        '''

        :param id: generally the filename
        :param channel:  the channel (if stereo).  May be a string list (i.e. "1,2").  No validation is done for the channel value
        :param label: a "class" label for this segment.  Examples include speaker, language, speech, etc
        :param start_t: the start time in seconds
        :param end_t: the end time in seconds
        :param decimal: if true, value is stored as a float
        '''
        self.id      = id
        self.channel = channel
        self.label   = label
        # Using Decimal instead of floats made the code 100x slower
        # Use floats for now in the intrest of speed

        if decimal:
            self.start_t = start_t
            self.end_t   = end_t
        else:
            self.start_t = float(start_t)
            self.end_t   = float(end_t)

        if(self.start_t > self.end_t):
            raise Exception("Start is after end in PemRecord: {self}".format(**vars()))

    def __eq__(self, other):
        if isinstance(other, PemRecord):
            return (self.id == other.id and self.channel == other.channel and self.label == other.label
                    and self.start_t == other.start_t and self.end_t == other.end_t)
        else:
            return False

    def __ne__(self, other):
        if isinstance(other, PemRecord):
            return (self.id != other.id or self.channel != other.channel or self.label != other.label
                    or self.start_t != other.start_t or self.end_t != other.end_t)
        else:
            return True

    def __str__(self):
        return "{self.id} {self.channel} {self.label} {self.start_t:.3f} {self.end_t:.3f}".format(**vars())

    def duration(self):
        return self.end_t - self.start_t

    def split_channels(self):
        '''
        Split the channel into an array, so that if a channel value of '1,2' is supplied it is returned as an array [1,2]
        :return: an array of channel numbers
        '''
        channels = []
        if type(self.channel) is str:
            # convert to a list
            channels = list(map(int, str.split(self.channel, ',')))
        elif type(self.channel) is int:
            channels.append(self.channel)
        else:
            print("Unsupported channel value: {}".format(self.channel))

        return channels

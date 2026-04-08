from dev.bscs.common import Application
from dev.bscs.events import EventListener
from dev.bscs.events import EventLoop
from dev.bscs.bacnet.stack import Device
from dev.bscs.bacnet.stack.data import BACnetPropertyIdentifier
from dev.bscs.bacnet.stack.data.base import BACnetCharacterString
from dev.bscs.bacnet.stack.data.base import BACnetObjectIdentifier
from dev.bscs.bacnet.stack.services import WhoIsClient
from dev.bscs.bacnet.stack.services import WritePropertyClient
from dev.bscs.bacnet.bacnetsc import SCDatalink
from dev.bscs.bacnet.bacnetsc import SCProperties
from java.io import BufferedReader
from java.io import BufferedWriter
from java.io import FileReader
from java.io import FileWriter
from java.lang import System
import random


class BACnetSCBenchmarkClient(Application):

    class Driver(EventListener):
        def __init__(self, parent):
            self.parent = parent

        def handleEvent(self, source, eventType, *args):
            self.parent._drive()

    class DiscoveryClient(WhoIsClient):
        def __init__(self, parent):
            self.parent = parent

        def success(self, device, binding, auth):
            if binding.instance == self.parent.server_instance:
                self.parent.target_dnet = binding.dnet
                self.parent.target_dadr = binding.dadr
                self.parent.binding_ready = True
                EventLoop.emit(self, self.parent.driver, EventLoop.EVENT_MAINTENANCE)

    class CsvWriteClient(WritePropertyClient):
        def __init__(self, parent):
            self.parent = parent

        def success(self, device, auth):
            self.parent._on_write_result(True, None)

        def failure(self, device, failure, auth):
            self.parent._on_write_result(False, str(failure))

    def start(self):
        cfg = Application.configuration

        self.csv_path = cfg.getString("app.csvPath", "/shared/HVAC-minute.csv")
        self.raw_output_path = cfg.getString("app.rawOutputPath", "/shared/results_rtt_bacnet-sc-tls.txt")
        self.max_rows = cfg.getInteger("app.maxRows", 0)
        self.server_instance = cfg.getInteger("app.serverDeviceInstance", 555001)
        self.target_property = cfg.getInteger("app.targetPropertyId", BACnetPropertyIdentifier.OBJECT_NAME)
        self.bind_retry_ms = cfg.getInteger("app.bindRetryMs", 1000)
        self.reconnect_every_rows = cfg.getInteger("app.reconnectEveryRows", 0)
        self.reconnect_jitter_rows = cfg.getInteger("app.reconnectJitterRows", 0)
        self.reconnect_pause_ms = cfg.getInteger("app.reconnectPauseMs", 0)
        self.reconnect_seed = cfg.getInteger("app.reconnectSeed", -1)

        self.target_object = BACnetObjectIdentifier.combine(8, self.server_instance)

        self.rows_seen = 0
        self.rows_valid = 0
        self.rows_skipped = 0
        self.rows_sent = 0

        self.pending = False
        self.binding_ready = False
        self.target_dnet = 0
        self.target_dadr = None
        self.last_bind_attempt_ms = 0
        self.reconnecting = False
        self.reconnect_restart_after_ms = 0
        self.next_reconnect_at_rows = 0

        self.start_ns = 0
        self.closed = False
        self.finished = False

        if self.reconnect_seed >= 0:
            self.rand = random.Random(self.reconnect_seed)
        else:
            self.rand = random.Random()

        self._schedule_next_reconnect()

        self.csv_reader = BufferedReader(FileReader(self.csv_path))
        self.raw_writer = BufferedWriter(FileWriter(self.raw_output_path))

        self.header_skipped = False

        self.device = Device(cfg)
        self.sc_datalink = SCDatalink("SC-1", SCProperties(cfg), self.device, 0)
        self.sc_datalink.start()

        self.driver = BACnetSCBenchmarkClient.Driver(self)
        self.discovery_client = BACnetSCBenchmarkClient.DiscoveryClient(self)
        self.write_client = BACnetSCBenchmarkClient.CsvWriteClient(self)

        EventLoop.addMaintenance(self.driver)
        EventLoop.emit(self, self.driver, EventLoop.EVENT_MAINTENANCE)

    def _next_reconnect_interval(self):
        if self.reconnect_every_rows <= 0:
            return 0

        jitter = self.reconnect_jitter_rows
        if jitter < 0:
            jitter = 0

        if jitter == 0:
            return self.reconnect_every_rows

        lower = self.reconnect_every_rows - jitter
        upper = self.reconnect_every_rows + jitter
        if lower < 1:
            lower = 1
        if upper < lower:
            upper = lower
        return self.rand.randint(lower, upper)

    def _schedule_next_reconnect(self):
        if self.reconnect_every_rows <= 0:
            self.next_reconnect_at_rows = 0
            return
        self.next_reconnect_at_rows = self.rows_sent + self._next_reconnect_interval()

    def _begin_reconnect(self, reason):
        if self.reconnecting or self.finished:
            return

        self.reconnecting = True
        self.pending = False
        self.binding_ready = False
        self.target_dnet = 0
        self.target_dadr = None
        self.reconnect_restart_after_ms = System.currentTimeMillis() + max(0, self.reconnect_pause_ms)

        try:
            if self.sc_datalink is not None:
                self.sc_datalink.stop()
        except:
            pass

        try:
            if self.sc_datalink is not None:
                self.sc_datalink.close()
        except:
            pass

        self.sc_datalink = None
        print("SC reconnect scheduled: reason=%s sent=%d resumeInMs=%d" % (
            reason,
            self.rows_sent,
            max(0, self.reconnect_pause_ms),
        ))
        EventLoop.emit(self, self.driver, EventLoop.EVENT_MAINTENANCE)

    def _continue_reconnect(self):
        if not self.reconnecting:
            return False

        now_ms = System.currentTimeMillis()
        if now_ms < self.reconnect_restart_after_ms:
            return True

        self.sc_datalink = SCDatalink("SC-1", SCProperties(Application.configuration), self.device, 0)
        self.sc_datalink.start()

        self.reconnecting = False
        self.last_bind_attempt_ms = 0
        self._schedule_next_reconnect()

        print("SC reconnect completed: sent=%d nextReconnectAt=%d" % (
            self.rows_sent,
            self.next_reconnect_at_rows,
        ))
        return False

    def addCommands(self):
        # No interactive commands needed for batch benchmark mode.
        pass

    def _next_payload(self):
        while True:
            line = self.csv_reader.readLine()
            if line is None:
                return None

            if not self.header_skipped:
                self.header_skipped = True
                continue

            self.rows_seen += 1

            parts = line.split(",", -1)
            if len(parts) < 9:
                self.rows_skipped += 1
                continue

            values = []
            valid = True
            for idx in [3, 4, 5, 6, 7, 8]:
                token = parts[idx].strip()
                if token == "":
                    valid = False
                    break
                try:
                    float(token)
                except:
                    valid = False
                    break
                values.append(token)

            if not valid:
                self.rows_skipped += 1
                continue

            self.rows_valid += 1
            return "|".join(values)

    def _on_write_result(self, success, failure_text):
        if self.finished:
            return

        self.pending = False

        if not success:
            self._finish(False, "WriteProperty failed: %s" % failure_text)
            return

        elapsed_us = int((System.nanoTime() - self.start_ns) / 1000)
        self.raw_writer.write(str(elapsed_us))
        self.raw_writer.newLine()

        self.rows_sent += 1
        if self.rows_sent % 1024 == 0:
            self.raw_writer.flush()

        if self.next_reconnect_at_rows > 0 and self.rows_sent >= self.next_reconnect_at_rows:
            self._begin_reconnect("periodic-rows")
            return

        EventLoop.emit(self, self.driver, EventLoop.EVENT_MAINTENANCE)

    def _drive(self):
        if self.finished:
            return

        if self._continue_reconnect():
            return

        if self.max_rows > 0 and self.rows_sent >= self.max_rows:
            self._finish(True, "Reached max rows")
            return

        if not self.binding_ready:
            now_ms = System.currentTimeMillis()
            if now_ms - self.last_bind_attempt_ms >= self.bind_retry_ms:
                self.last_bind_attempt_ms = now_ms
                self.discovery_client.request(self.device, 65535, None, self.server_instance, self.server_instance)
            return

        if self.pending:
            return

        payload = self._next_payload()
        if payload is None:
            self._finish(True, "CSV exhausted")
            return

        self.start_ns = System.nanoTime()
        sent = self.write_client.request(
            self.device,
            self.target_dnet,
            self.target_dadr,
            self.target_object,
            self.target_property,
            -1,
            BACnetCharacterString(payload),
        )

        if not sent:
            self._finish(False, "WriteProperty request could not be sent")
            return

        self.pending = True

    def _finish(self, ok, reason):
        if self.finished:
            return

        self.finished = True
        EventLoop.removeMaintenance(self.driver)

        try:
            self.raw_writer.flush()
        except:
            pass

        print("SC benchmark finished: ok=%s reason=%s sent=%d valid=%d skipped=%d seen=%d" % (
            str(ok),
            reason,
            self.rows_sent,
            self.rows_valid,
            self.rows_skipped,
            self.rows_seen,
        ))

        if ok:
            Application.shutdown("BACnet/SC benchmark completed", True)
        else:
            Application.shutdown("BACnet/SC benchmark failed: %s" % reason, True)

    def stop(self):
        if self.sc_datalink is not None:
            self.sc_datalink.stop()

    def close(self):
        if self.closed:
            return
        self.closed = True

        try:
            if self.csv_reader is not None:
                self.csv_reader.close()
        except:
            pass

        try:
            if self.raw_writer is not None:
                self.raw_writer.close()
        except:
            pass

        try:
            if self.sc_datalink is not None:
                self.sc_datalink.close()
        except:
            pass

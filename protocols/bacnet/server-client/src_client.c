#define _POSIX_C_SOURCE 200809L

#include <errno.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "bacnet/apdu.h"
#include "bacnet/arf.h"
#include "bacnet/bacapp.h"
#include "bacnet/bacdef.h"
#include "bacnet/bacstr.h"
#include "bacnet/bactext.h"
#include "bacnet/iam.h"
#include "bacnet/npdu.h"
#include "bacnet/whois.h"
#include "bacnet/basic/binding/address.h"
#include "bacnet/basic/object/device.h"
#include "bacnet/basic/services.h"
#include "bacnet/basic/tsm/tsm.h"
#include "bacnet/datalink/datalink.h"
#include "bacnet/datalink/dlenv.h"

#define DEFAULT_SERVER_IP "200.1.1.9"
#define DEFAULT_SERVER_PORT 47808
#define DEFAULT_TARGET_DEVICE_INSTANCE 2001
#define DEFAULT_TARGET_OBJECT_INSTANCE 1
#define DEFAULT_TIMEOUT_MS 5000
#define DEFAULT_WRITE_RETRIES 2
#define DEFAULT_RETRY_DELAY_MS 250
#define LINE_BUFFER_SIZE 2048
#define PAYLOAD_BUFFER_SIZE 256

struct options {
    const char *csv_path;
    const char *server_ip;
    int server_port;
    const char *scenario;
    const char *raw_output_path;
    int timeout_ms;
    int write_retries;
    int retry_delay_ms;
    long max_rows;
    uint32_t target_device_instance;
    uint32_t target_object_instance;
};

static uint8_t Rx_Buf[MAX_MPDU] = { 0 };
static BACNET_ADDRESS Target_Address;
static uint8_t Request_Invoke_ID;
static bool Write_Ack_Received;
static bool Request_Failed;
static char Failure_Reason[160];
static time_t Timer_Last_Seconds;

static void print_usage(const char *prog)
{
    fprintf(stderr,
            "Usage: %s --csv <path> [--server-ip <ip>] [--server-port <port>]\n"
            "          [--target-device <instance>] [--target-object-instance <instance>]\n"
            "          [--scenario <name>] [--raw-output <path>] [--timeout-ms <ms>]\n"
            "          [--write-retries <n>] [--retry-delay-ms <ms>]\n"
            "          [--max-rows <n>]\n"
            "\n"
            "Required:\n"
            "  --csv <path>                     CSV input path (HVAC-minute.csv)\n"
            "Optional:\n"
            "  --server-ip <ip>                 BACnet server IP (default: %s)\n"
            "  --server-port <port>             BACnet/IP UDP port (default: %d)\n"
            "  --target-device <instance>       BACnet Device object instance (default: %u)\n"
            "  --target-object-instance <inst>  CharacterString Value object instance (default: %u)\n"
            "  --scenario <name>                Scenario label used in logs (default: plain)\n"
            "  --raw-output <path>              Output file with RTT samples (default: rtt_<scenario>.txt)\n"
            "  --timeout-ms <ms>                APDU timeout per request (default: %d)\n"
            "  --write-retries <n>              Retries for timeout-like write failures (default: %d)\n"
            "  --retry-delay-ms <ms>            Delay between write retries (default: %d)\n"
            "  --max-rows <n>                   Stop after n valid CSV rows (default: 0 = all rows)\n",
            prog,
            DEFAULT_SERVER_IP,
            DEFAULT_SERVER_PORT,
            DEFAULT_TARGET_DEVICE_INSTANCE,
            DEFAULT_TARGET_OBJECT_INSTANCE,
            DEFAULT_TIMEOUT_MS,
            DEFAULT_WRITE_RETRIES,
            DEFAULT_RETRY_DELAY_MS);
}

static bool parse_long(const char *s, long *out)
{
    char *end = NULL;
    long value;

    errno = 0;
    value = strtol(s, &end, 10);
    if (errno != 0 || end == s || *end != '\0') {
        return false;
    }
    *out = value;
    return true;
}

static bool parse_double_token(const char *token, double *out)
{
    char *end = NULL;
    double value;

    if (token == NULL || token[0] == '\0') {
        return false;
    }

    errno = 0;
    value = strtod(token, &end);
    if (errno != 0 || end == token) {
        return false;
    }

    while (*end == ' ' || *end == '\t' || *end == '\r' || *end == '\n') {
        end++;
    }
    if (*end != '\0') {
        return false;
    }

    *out = value;
    return true;
}

static bool extract_hvac_values(const char *line, double values[6])
{
    char tmp[LINE_BUFFER_SIZE];
    char *saveptr = NULL;
    char *token = NULL;
    int col = 0;
    int captured = 0;

    strncpy(tmp, line, sizeof(tmp) - 1);
    tmp[sizeof(tmp) - 1] = '\0';

    token = strtok_r(tmp, ",", &saveptr);
    while (token != NULL) {
        col++;
        if (col >= 4 && col <= 9) {
            if (!parse_double_token(token, &values[captured])) {
                return false;
            }
            captured++;
        }
        token = strtok_r(NULL, ",", &saveptr);
    }

    return captured == 6;
}

static bool parse_options(int argc, char **argv, struct options *opts)
{
    int i;

    memset(opts, 0, sizeof(*opts));
    opts->server_ip = DEFAULT_SERVER_IP;
    opts->server_port = DEFAULT_SERVER_PORT;
    opts->target_device_instance = DEFAULT_TARGET_DEVICE_INSTANCE;
    opts->target_object_instance = DEFAULT_TARGET_OBJECT_INSTANCE;
    opts->scenario = "plain";
    opts->timeout_ms = DEFAULT_TIMEOUT_MS;
    opts->write_retries = DEFAULT_WRITE_RETRIES;
    opts->retry_delay_ms = DEFAULT_RETRY_DELAY_MS;
    opts->max_rows = 0;

    for (i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--csv") == 0 && i + 1 < argc) {
            opts->csv_path = argv[++i];
        } else if (strcmp(argv[i], "--server-ip") == 0 && i + 1 < argc) {
            opts->server_ip = argv[++i];
        } else if (strcmp(argv[i], "--server-port") == 0 && i + 1 < argc) {
            long port = 0;
            if (!parse_long(argv[++i], &port) || port <= 0 || port > 65535) {
                fprintf(stderr, "Invalid --server-port value\n");
                return false;
            }
            opts->server_port = (int)port;
        } else if (strcmp(argv[i], "--target-device") == 0 && i + 1 < argc) {
            long instance = 0;
            if (!parse_long(argv[++i], &instance) ||
                instance < 0 || instance > BACNET_MAX_INSTANCE) {
                fprintf(stderr, "Invalid --target-device value\n");
                return false;
            }
            opts->target_device_instance = (uint32_t)instance;
        } else if (strcmp(argv[i], "--target-object-instance") == 0 && i + 1 < argc) {
            long instance = 0;
            if (!parse_long(argv[++i], &instance) ||
                instance < 0 || instance > BACNET_MAX_INSTANCE) {
                fprintf(stderr, "Invalid --target-object-instance value\n");
                return false;
            }
            opts->target_object_instance = (uint32_t)instance;
        } else if (strcmp(argv[i], "--scenario") == 0 && i + 1 < argc) {
            opts->scenario = argv[++i];
        } else if (strcmp(argv[i], "--raw-output") == 0 && i + 1 < argc) {
            opts->raw_output_path = argv[++i];
        } else if (strcmp(argv[i], "--timeout-ms") == 0 && i + 1 < argc) {
            long timeout = 0;
            if (!parse_long(argv[++i], &timeout) || timeout <= 0 || timeout > 65535) {
                fprintf(stderr, "Invalid --timeout-ms value (1..65535)\n");
                return false;
            }
            opts->timeout_ms = (int)timeout;
        } else if (strcmp(argv[i], "--write-retries") == 0 && i + 1 < argc) {
            long write_retries = 0;
            if (!parse_long(argv[++i], &write_retries) ||
                write_retries < 0 || write_retries > 100) {
                fprintf(stderr, "Invalid --write-retries value (0..100)\n");
                return false;
            }
            opts->write_retries = (int)write_retries;
        } else if (strcmp(argv[i], "--retry-delay-ms") == 0 && i + 1 < argc) {
            long retry_delay_ms = 0;
            if (!parse_long(argv[++i], &retry_delay_ms) ||
                retry_delay_ms < 0 || retry_delay_ms > 60000) {
                fprintf(stderr, "Invalid --retry-delay-ms value (0..60000)\n");
                return false;
            }
            opts->retry_delay_ms = (int)retry_delay_ms;
        } else if (strcmp(argv[i], "--max-rows") == 0 && i + 1 < argc) {
            long max_rows = 0;
            if (!parse_long(argv[++i], &max_rows) || max_rows < 0) {
                fprintf(stderr, "Invalid --max-rows value\n");
                return false;
            }
            opts->max_rows = max_rows;
        } else if (strcmp(argv[i], "--help") == 0 || strcmp(argv[i], "-h") == 0) {
            print_usage(argv[0]);
            exit(0);
        } else {
            fprintf(stderr, "Unknown or incomplete option: %s\n", argv[i]);
            return false;
        }
    }

    if (opts->csv_path == NULL) {
        fprintf(stderr, "Missing required --csv option\n");
        return false;
    }

    return true;
}

static bool is_retryable_write_failure(const char *reason)
{
    if (reason == NULL) {
        return false;
    }

    return strstr(reason, "TSM timeout") != NULL ||
        strstr(reason, "APDU timeout") != NULL;
}

static void sleep_ms(int ms)
{
    struct timespec ts = { 0 };

    if (ms <= 0) {
        return;
    }

    ts.tv_sec = ms / 1000;
    ts.tv_nsec = (long)(ms % 1000) * 1000000L;
    while (nanosleep(&ts, &ts) == -1 && errno == EINTR) {
        ;
    }
}

static long rtt_us(const struct timespec *start, const struct timespec *end)
{
    time_t sec = end->tv_sec - start->tv_sec;
    long nsec = end->tv_nsec - start->tv_nsec;

    if (nsec < 0) {
        sec -= 1;
        nsec += 1000000000L;
    }

    return (long)(sec * 1000000L + nsec / 1000L);
}

static void format_failure(
    const char *prefix, const char *detail, char *buf, size_t buf_size)
{
    if (detail && detail[0] != '\0') {
        snprintf(buf, buf_size, "%s: %s", prefix, detail);
    } else {
        snprintf(buf, buf_size, "%s", prefix);
    }
}

static void MyErrorHandler(
    BACNET_ADDRESS *src,
    uint8_t invoke_id,
    BACNET_ERROR_CLASS error_class,
    BACNET_ERROR_CODE error_code)
{
    if (!address_match(&Target_Address, src) || invoke_id != Request_Invoke_ID) {
        return;
    }

    Request_Failed = true;
    snprintf(Failure_Reason,
             sizeof(Failure_Reason),
             "BACnet Error %s/%s",
             bactext_error_class_name((int)error_class),
             bactext_error_code_name((int)error_code));
}

static void MyAbortHandler(
    BACNET_ADDRESS *src, uint8_t invoke_id, uint8_t abort_reason, bool server)
{
    (void)server;
    if (!address_match(&Target_Address, src) || invoke_id != Request_Invoke_ID) {
        return;
    }

    Request_Failed = true;
    snprintf(Failure_Reason,
             sizeof(Failure_Reason),
             "BACnet Abort %s",
             bactext_abort_reason_name((int)abort_reason));
}

static void
MyRejectHandler(BACNET_ADDRESS *src, uint8_t invoke_id, uint8_t reject_reason)
{
    if (!address_match(&Target_Address, src) || invoke_id != Request_Invoke_ID) {
        return;
    }

    Request_Failed = true;
    snprintf(Failure_Reason,
             sizeof(Failure_Reason),
             "BACnet Reject %s",
             bactext_reject_reason_name((int)reject_reason));
}

static void MyWritePropertySimpleAckHandler(BACNET_ADDRESS *src, uint8_t invoke_id)
{
    if (!address_match(&Target_Address, src) || invoke_id != Request_Invoke_ID) {
        return;
    }

    Write_Ack_Received = true;
}

static void Init_Service_Handlers(void)
{
    Device_Init(NULL);
    apdu_set_unconfirmed_handler(SERVICE_UNCONFIRMED_WHO_IS, handler_who_is);
    apdu_set_unconfirmed_handler(SERVICE_UNCONFIRMED_I_AM, handler_i_am_bind);
    apdu_set_unrecognized_service_handler_handler(handler_unrecognized_service);
    apdu_set_confirmed_handler(
        SERVICE_CONFIRMED_READ_PROPERTY, handler_read_property);
    apdu_set_confirmed_simple_ack_handler(
        SERVICE_CONFIRMED_WRITE_PROPERTY, MyWritePropertySimpleAckHandler);
    apdu_set_error_handler(SERVICE_CONFIRMED_WRITE_PROPERTY, MyErrorHandler);
    apdu_set_abort_handler(MyAbortHandler);
    apdu_set_reject_handler(MyRejectHandler);
}

static void pump_timers(void)
{
    time_t current_seconds = time(NULL);

    if (Timer_Last_Seconds == 0) {
        Timer_Last_Seconds = current_seconds;
        return;
    }
    if (current_seconds > Timer_Last_Seconds) {
        uint16_t delta_seconds = (uint16_t)(current_seconds - Timer_Last_Seconds);
        tsm_timer_milliseconds((uint16_t)(delta_seconds * 1000U));
        datalink_maintenance_timer(delta_seconds);
        address_cache_timer(delta_seconds);
        Timer_Last_Seconds = current_seconds;
    }
}

static bool process_receive_once(unsigned timeout_ms)
{
    BACNET_ADDRESS src = { 0 };
    uint16_t pdu_len = 0;

    pdu_len = datalink_receive(&src, &Rx_Buf[0], MAX_MPDU, timeout_ms);
    if (pdu_len > 0) {
        npdu_handler(&src, &Rx_Buf[0], pdu_len);
        return true;
    }
    return false;
}

static bool seed_direct_target(
    uint32_t target_device_instance, const char *server_ip, int server_port)
{
    BACNET_MAC_ADDRESS mac = { 0 };
    BACNET_ADDRESS dest = { 0 };
    char mac_text[64];

    if (snprintf(mac_text, sizeof(mac_text), "%s:%d", server_ip, server_port) <= 0) {
        return false;
    }

    if (!address_mac_from_ascii(&mac, mac_text)) {
        return false;
    }
    if (!bacnet_address_init(&dest, &mac, 0, NULL)) {
        return false;
    }

    address_add(target_device_instance, MAX_APDU, &dest);
    return true;
}

static bool bind_target_device(uint32_t target_device_instance, int timeout_ms)
{
    unsigned max_apdu = 0;
    bool found = false;
    struct timespec start;
    struct timespec now;

    clock_gettime(CLOCK_MONOTONIC, &start);
    Send_WhoIs(target_device_instance, target_device_instance);

    for (;;) {
        long elapsed_ms;

        pump_timers();
        found = address_bind_request(
            target_device_instance, &max_apdu, &Target_Address);
        if (found) {
            return true;
        }

        process_receive_once(25);

        clock_gettime(CLOCK_MONOTONIC, &now);
        elapsed_ms = (now.tv_sec - start.tv_sec) * 1000L +
            (now.tv_nsec - start.tv_nsec) / 1000000L;
        if (elapsed_ms >= timeout_ms) {
            return false;
        }
    }
}

static bool send_row_and_wait(
    uint32_t target_device_instance,
    uint32_t target_object_instance,
    const char *payload,
    int timeout_ms)
{
    BACNET_APPLICATION_DATA_VALUE value = { 0 };
    struct timespec start;
    struct timespec now;

    Request_Failed = false;
    Write_Ack_Received = false;
    Failure_Reason[0] = '\0';

    value.context_specific = false;
    value.context_tag = 0;
    value.tag = BACNET_APPLICATION_TAG_CHARACTER_STRING;
    if (!characterstring_init_ansi(&value.type.Character_String, payload)) {
        format_failure("characterstring_init_ansi failed", NULL, Failure_Reason, sizeof(Failure_Reason));
        return false;
    }

    Request_Invoke_ID = Send_Write_Property_Request(
        target_device_instance,
        OBJECT_CHARACTERSTRING_VALUE,
        target_object_instance,
        PROP_PRESENT_VALUE,
        &value,
        BACNET_NO_PRIORITY,
        BACNET_ARRAY_ALL);
    if (Request_Invoke_ID == 0) {
        format_failure("Send_Write_Property_Request failed", NULL, Failure_Reason, sizeof(Failure_Reason));
        return false;
    }

    clock_gettime(CLOCK_MONOTONIC, &start);

    for (;;) {
        long elapsed_ms;

        pump_timers();
        process_receive_once(25);

        if (Request_Failed) {
            return false;
        }
        if (Write_Ack_Received || tsm_invoke_id_free(Request_Invoke_ID)) {
            return true;
        }
        if (tsm_invoke_id_failed(Request_Invoke_ID)) {
            tsm_free_invoke_id(Request_Invoke_ID);
            format_failure("TSM timeout", NULL, Failure_Reason, sizeof(Failure_Reason));
            return false;
        }

        clock_gettime(CLOCK_MONOTONIC, &now);
        elapsed_ms = (now.tv_sec - start.tv_sec) * 1000L +
            (now.tv_nsec - start.tv_nsec) / 1000000L;
        if (elapsed_ms >= timeout_ms) {
            tsm_free_invoke_id(Request_Invoke_ID);
            snprintf(Failure_Reason, sizeof(Failure_Reason), "APDU timeout (%d ms)", timeout_ms);
            return false;
        }
    }
}

int main(int argc, char **argv)
{
    struct options opts;
    FILE *csv = NULL;
    FILE *raw = NULL;
    char line[LINE_BUFFER_SIZE];
    bool header_skipped = false;
    long rows_seen = 0;
    long rows_valid = 0;
    long rows_sent = 0;
    long rows_skipped = 0;
    long rtt_min = 0;
    long rtt_max = 0;
    long double rtt_sum = 0.0;
    char payload[PAYLOAD_BUFFER_SIZE];

    if (!parse_options(argc, argv, &opts)) {
        print_usage(argv[0]);
        return 1;
    }

    csv = fopen(opts.csv_path, "r");
    if (csv == NULL) {
        perror("Failed to open CSV file");
        return 1;
    }

    char default_raw_output[256];
    if (opts.raw_output_path == NULL) {
        snprintf(default_raw_output, sizeof(default_raw_output), "rtt_%s.txt", opts.scenario);
        opts.raw_output_path = default_raw_output;
    }

    raw = fopen(opts.raw_output_path, "w");
    if (raw == NULL) {
        perror("Failed to open raw output file");
        fclose(csv);
        return 1;
    }

    address_init();
    Device_Set_Object_Instance_Number(BACNET_MAX_INSTANCE);
    Init_Service_Handlers();
    apdu_timeout_set((uint16_t)opts.timeout_ms);
    apdu_retries_set(0);
    dlenv_init();
    atexit(datalink_cleanup);
    Timer_Last_Seconds = 0;
    memset(&Target_Address, 0, sizeof(Target_Address));

    if (!seed_direct_target(
            opts.target_device_instance, opts.server_ip, opts.server_port)) {
        fprintf(stderr,
                "Failed to seed direct binding for %s:%d\n",
                opts.server_ip,
                opts.server_port);
    }

    if (!bind_target_device(opts.target_device_instance, opts.timeout_ms)) {
        fprintf(stderr,
                "Unable to bind BACnet target device instance %u (server %s:%d)\n",
                (unsigned)opts.target_device_instance,
                opts.server_ip,
                opts.server_port);
        fclose(raw);
        fclose(csv);
        return 1;
    }

    while (fgets(line, sizeof(line), csv) != NULL) {
        double values[6];
        struct timespec t0 = { 0 };
        struct timespec t1 = { 0 };
        long sample_rtt;
        bool sent_ok = false;
        int write_attempt;

        if (!header_skipped) {
            header_skipped = true;
            continue;
        }

        rows_seen++;

        if (!extract_hvac_values(line, values)) {
            rows_skipped++;
            continue;
        }

        rows_valid++;
        if (opts.max_rows > 0 && rows_sent >= opts.max_rows) {
            break;
        }

        snprintf(payload,
                 sizeof(payload),
                 "%.6f|%.6f|%.6f|%.6f|%.6f|%.6f",
                 values[0],
                 values[1],
                 values[2],
                 values[3],
                 values[4],
                 values[5]);

        for (write_attempt = 0; write_attempt <= opts.write_retries; write_attempt++) {
            clock_gettime(CLOCK_MONOTONIC, &t0);
            if (send_row_and_wait(
                    opts.target_device_instance,
                    opts.target_object_instance,
                    payload,
                    opts.timeout_ms)) {
                clock_gettime(CLOCK_MONOTONIC, &t1);
                sent_ok = true;
                break;
            }

            if (!is_retryable_write_failure(Failure_Reason) ||
                write_attempt >= opts.write_retries) {
                break;
            }

            fprintf(stderr,
                    "Retrying row %ld (%d/%d) after transient failure: %s\n",
                    rows_seen,
                    write_attempt + 1,
                    opts.write_retries,
                    Failure_Reason);
            sleep_ms(opts.retry_delay_ms);
            if (!bind_target_device(opts.target_device_instance, opts.timeout_ms)) {
                snprintf(Failure_Reason,
                         sizeof(Failure_Reason),
                         "Unable to re-bind BACnet target device instance %u",
                         (unsigned)opts.target_device_instance);
                break;
            }
        }

        if (!sent_ok) {
            fprintf(stderr, "WriteProperty failed at row %ld: %s\n", rows_seen, Failure_Reason);
            fclose(raw);
            fclose(csv);
            return 1;
        }

        sample_rtt = rtt_us(&t0, &t1);
        fprintf(raw, "%ld\n", sample_rtt);

        if (rows_sent == 0) {
            rtt_min = sample_rtt;
            rtt_max = sample_rtt;
        } else {
            if (sample_rtt < rtt_min) {
                rtt_min = sample_rtt;
            }
            if (sample_rtt > rtt_max) {
                rtt_max = sample_rtt;
            }
        }
        rtt_sum += (long double)sample_rtt;

        rows_sent++;
    }

    fflush(raw);

    if (rows_sent > 0) {
        long double avg = rtt_sum / (long double)rows_sent;
        fprintf(stderr,
                "Scenario=%s sent=%ld valid=%ld skipped=%ld seen=%ld RTT_us[min=%ld max=%ld avg=%.3Lf]\n",
                opts.scenario,
                rows_sent,
                rows_valid,
                rows_skipped,
                rows_seen,
                rtt_min,
                rtt_max,
                avg);
    } else {
        fprintf(stderr,
                "Scenario=%s sent=0 valid=%ld skipped=%ld seen=%ld\n",
                opts.scenario,
                rows_valid,
                rows_skipped,
                rows_seen);
    }

    fclose(raw);
    fclose(csv);
    return 0;
}

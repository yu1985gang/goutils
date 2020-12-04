import logging

LOGGER = logging.getLogger('cctf_api')
FILE_HANDLER = logging.FileHandler('cctf_api.log')
STREAM_HANDLER = logging.StreamHandler()
FORMATTER = logging.Formatter(
    '%(asctime)s - %(name)s - [%(filename)s:%(lineno)d] - %(levelname)s - %(message)s'
)

FILE_HANDLER.setFormatter(FORMATTER)
STREAM_HANDLER.setFormatter(FORMATTER)

def set_logger_log_level(log_level):
    if log_level in 'DEBUG':
        LOGGER.setLevel(logging.DEBUG)
        FILE_HANDLER.setLevel(logging.DEBUG)
        STREAM_HANDLER.setLevel(logging.DEBUG)
    else:
        LOGGER.setLevel(logging.INFO)
        FILE_HANDLER.setLevel(logging.INFO)
        STREAM_HANDLER.setLevel(logging.INFO)

    LOGGER.addHandler(FILE_HANDLER)
    LOGGER.addHandler(STREAM_HANDLER)

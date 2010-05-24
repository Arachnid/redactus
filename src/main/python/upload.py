import httplib
import logging
import optparse
import os
import mimetypes
import Queue
import simplejson
import sys
import threading


def parse_command_line(argv):
  parser = optparse.OptionParser(usage="%prog [options] dir")
  parser.add_option("-s", "--server", dest="server", help="Server to upload to",
                    default="redactus.org")
  parser.add_option("-p", "--port", dest="port", help="Server port", default=80,
                    type="int")
  parser.add_option("-v", "--verbose", dest="verbose", action="store_true",
                    help="Be verbose", default=False)
  options, args = parser.parse_args()
  if len(args) < 1:
    parser.error("Required directory parameter omitted")
  if len(args) > 1:
    parser.error("Only one directory parameter may be included")
  return options, args[0]


def guess_type(path):
  type, encoding = mimetypes.guess_type(path)
  return type or "application/octet-stream"


def read_file(path):
  fh = open(path, "rb")
  data = fh.read()
  fh.close()
  return data


class UploadTask(object):
  def __init__(self, options, directory):
    self.options = options
    self.directory = directory

  def run(self):
    self.connection = self.create_connection()
    manifest = []
    try:
      for dirpath, dirnames, filenames in os.walk(self.directory):
        work_items = self.generate_work_items(dirpath, filenames)
        for path, headers, relpaths in work_items:
          logging.info("Uploading file '%s'", relpaths[0])
          hash = self.upload_file(headers, read_file(path))
          logging.info("  %s", hash)
          manifest.extend((hash, p) for p in relpaths)

      return self.upload_manifest(manifest)
    finally:
      self.connection.close()

  def create_connection(self):
    return httplib.HTTPConnection(self.options.server, self.options.port)

  def upload_file(self, headers, body):
    self.connection.request("POST", "/_upload/", body, headers)
    response = self.connection.getresponse()
    if response.status / 100 != 2:
      raise Exception("Received unexpected status code: %d %s" %
                      (response.status, response.reason))
    response_data = simplejson.loads(response.read())
    if len(response_data) != 1:
      raise Exception("Upload failed: Expected one response element, got %d"
                      % len(response_data))
    response_data = response_data[0]
    if response_data.get("status") != "success":
      raise Exception("Upload failed: %s" % (response_data.get("error"),))
    return response_data["id"]

  def generate_work_items(self, dirpath, filenames):
    dir_map = {}
    for filename in filenames:
      path = os.path.join(dirpath, filename)
      relpath = path[len(os.path.commonprefix([path, self.directory])):]
      headers = {
          "Content-Type": guess_type(path),
      }
      dir_map[filename] = (path, headers, [relpath])
    for index_file in ["index.html", "index.htm"]:
      if index_file in dir_map:
        dir_map[index_file][2].append(os.path.dirname(relpath))
        break
    return dir_map.values()

  def upload_manifest(self, manifest):
    manifest.sort()
    manifest_str = "\n".join("\t".join(x) for x in manifest)
    headers = {
        "Content-Type": "text/x-redactus-manifest",
    }
    logging.info("Uploading manifest...")
    return self.upload_file(headers, manifest_str)


def main(argv):
  options, directory = parse_command_line(argv)
  logging.basicConfig(
      level=logging.DEBUG if options.verbose else logging.WARN)

  task = UploadTask(options, directory)
  manifest_hash = task.run()
  print manifest_hash
  

if __name__ == "__main__":
  main(sys.argv)

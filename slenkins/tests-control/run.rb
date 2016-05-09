#! /usr/bin/ruby

require 'twopence'
require 'time'

# DOCUMENTATION EXAMPLES https://github.com/okirch/twopence/blob/master/examples/example.rb

# Documentation about currents methods. 
# https://github.com/okirch/twopence/blob/a2f38fb4b4fb69141cad3c3ed6d5ac337911bb72/ruby/ext/twopence/target.c
##
##Example   rc, major, minor = target.test_and_print_results("ls -l", "johndoe")
## Input:
##  command: the command to run
##   user: the user under which to run the command
##         (optional, defaults to "root")
##   timeout: the time in seconds after which the command is aborted 
##            (optional, defaults to 60L)


# this is the run.rb in ruby. A Library still not exist yet, but with the environ variables, we can get
# all the variables we want . (IP of macines, FAMILY, etc, and we can run commands from control to 
# machines.



# Example for getting the target. We use the env-vars, for dinamically.

# this are static examples. 
#   $target = Twopence::init("virtio:/var/run/twopence/test.sock")
#   $target = Twopence::init("ssh:192.168.123.45")
#   $target = Twopence::init("serial:/dev/ttyS0")



#FIXME: jlogger should be created in ruby.
# HACK:  at moment without a library for ruby the stderr in jenkins will contain the stdout for cmd.
# jlogger functions. This in feature should be moved to a generic ruby library.
# For moment the priority is to run the cucumber suite.

def jlogger(keyword, id: nil, text: nil, hostname: nil, type: nil)
  # separator
  sep = "=" * 50
  s_time = " time=\"#{Time.now.utc.iso8601}\""
  s_id = id.nil? ? "": " id=\"#{id}\""
  s_text = text.nil? ? "": " text=\"#{text}\""
  s_hostname = hostname.nil? ? "": " hostname=\"#{hostname}\""
  s_type = type.nil? ? "": " type=\"#{type}\""
  
  STDOUT.puts sep
  STDOUT.puts "###junit #{keyword}#{s_time}#{s_id}#{s_text}#{s_hostname}#{s_type}"
  STDOUT.puts sep
  STDOUT.flush
end

def run_command(node, cmd, msg, timeout=60)
   jlogger("testcase", text: msg)
   local, remote, command = node.test_and_print_results(cmd , "root", timeout)
   if (local == 0 && remote == 0 && command == 0)
      jlogger("success")
   else
      jlogger("failure")
   end
end
#Create object twopence
CLIENT = Twopence::init(ENV["TARGET_CLIENT"])
SERVER = Twopence::init(ENV["TARGET_SERVER"])
MINION = Twopence::init(ENV["TARGET_MINION"])

# variables that can be accessed and passed  with slenkins.

# http://slenkins.suse.de/doc/REFERENCE-variables.txt
# FIXME: in future, move this to attribute for the object, like susetest.

# For moment this work fine too for ruby. no PRIO1
CLIENT_IP = ENV["INTERNAL_IP_CLIENT"]
# this get the sles-sp1 or sp2 etc distro var.
CLIENT_FAMILY = ENV["FAMILY_CLIENT"]

family_hello = "echo #{CLIENT_FAMILY}"

#R!"
#jlogger("testsuite", id: GROUP_NAME, text: GROUP_NAME)
#run_command(CLIENT, "ip a s",  "ip from  CLIENT")
#run_command(SERVER, "ip a s",  "ip from  SERVER")
#run_command(MINION, "ip a s",  "ip from MINION")
#run_command(CLIENT, family_hello,  "FAMILY TESTS")



# ********************************************
## MAIN ##
# ********************************************
# Server configuration
GROUP_NAME = "INIT_SERVER FOR SUSE_MANAGER!"
SERVER_INIT= "/var/lib/slenkins/tests-suma-cucumber/tests-server/bin/suma_init.sh"
jlogger("testsuite", id: GROUP_NAME, text: GROUP_NAME)
run_command(SERVER, SERVER_INIT,  "INIT_SERVER", 1000)

jlogger("endsuite")

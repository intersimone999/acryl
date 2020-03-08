require "tmpdir"

IGNORE_APIS = (ARGV.delete("--ignore-apis") != nil)
DETAILED    = (ARGV.delete("--detailed")    != nil)

# if ARGV.size == 2
FOLDER          = ARGV[0]
RULESET         = ARGV[1]
APIS            = ARGV[2]
GRAPHS_FOLDER   = ARGV[3]

FAST_SWITCH = true
# elsif ARGV.size > 2
#     puts "Unsupported"
#     exit
# else
#     raise "Too few arguments"
# end

THIS = File.dirname(__FILE__)

tmp = File.join Dir.tmpdir, "ruleset_" + rand(100000).to_s
while FileTest.exist? tmp
    tmp = File.join Dir.tmpdir, "ruleset_" + rand(100000).to_s
end

TMP = tmp

`mkdir -p #{TMP}`

`ruby "#{File.join(THIS, "merger.rb")}" "#{FOLDER}" "#{TMP}/t_merged.csv"`
`ruby "#{File.join(THIS, "cleaner.rb")}" "#{TMP}/t_merged.csv" "#{THIS}/filter.txt" "#{TMP}/t_cleaned.csv"`
`ruby "#{File.join(THIS, "matcher.rb")}" "#{TMP}/t_cleaned.csv" "#{TMP}/t_matched.csv"`
`ruby "#{File.join(THIS, "build_ruleset.rb")}" "#{TMP}/t_matched.csv" "#{TMP}/t_ruleset.csv" "#{THIS}/stopwords.txt"`

detailed = DETAILED ? "y" : "n"
if GRAPHS_FOLDER
    `ruby "#{File.join(THIS, "refine_rules.rb")}" "#{TMP}/t_ruleset.csv" "#{TMP}/t_ruleset2.csv" "#{GRAPHS_FOLDER}" #{detailed}`
else
    `ruby "#{File.join(THIS, "refine_rules.rb")}" "#{TMP}/t_ruleset.csv" "#{TMP}/t_ruleset2.csv" "" #{detailed}`
end

unless IGNORE_APIS
    if FAST_SWITCH
        `ruby "#{File.join(THIS, "build_easy_apifile.rb")}" "#{APIS}" "#{TMP}/t_apifile.csv"`
        `ruby "#{File.join(THIS, "assign_easy_confidence.rb")}" "#{TMP}/t_ruleset2.csv" "#{TMP}/t_apifile.csv" "#{RULESET}"`
    else
        `ruby "#{File.join(THIS, "build_apifile.rb")}" "#{APIS}" "#{TMP}/t_apifile.csv"`
        `ruby "#{File.join(THIS, "assign_confidence.rb")}" "#{TMP}/t_ruleset2.csv" "#{TMP}/t_apifile.csv" "#{RULESET}"`
    end
else
    `cp "#{TMP}/t_ruleset2.csv" "#{RULESET}"`
end

`rm -r "#{TMP}"`

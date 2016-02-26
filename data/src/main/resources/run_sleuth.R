if (!require(sleuth)) {
    source("http://bioconductor.org/biocLite.R")
    biocLite("rhdf5")

    install.packages("devtools", repos = "http://cran.us.r-project.org")
    devtools::install_github("pachterlab/sleuth")
}

library(sleuth)


main <- function(output_path, design_path) {
    s2c <- read.csv(design_path, sep = "\t", stringsAsFactors = FALSE)
    so  <- sleuth_prep(s2c, ~ condition)
    so  <- sleuth_fit(so)
    so  <- sleuth_wt(so, "condition")
    results <- sleuth_results(so, "condition")
    write.table(results[complete.cases(results), ],
                file.path(output_path, "results.csv"),
                quote = F, sep = "\t", row.name = F)
}


if (!interactive()) {
    args <- commandArgs(TRUE)
    if (length(args) != 2) {
        write("Usage: [executable] path/to/output path/to/design.csv",
              stderr())
        q(status = 1)
    }

    do.call(main, as.list(args))
}

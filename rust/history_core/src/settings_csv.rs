use serde_json::Value;
use std::fmt::Write;

pub fn encode(input: &str) -> Option<String> {
    let rows: Value = serde_json::from_str(input).ok()?;
    let mut output =
        String::from("Date,Name,Category,Target,IsUnilateral,LogType,Sets_Detail\n");

    for row in rows.as_array()? {
        let fields = row.as_array()?;
        let [date, name, category, target, unilateral, log_type, sets] = fields.as_slice() else {
            return None;
        };
        let unilateral = unilateral.as_bool()?;
        write!(
            output,
            "{},{},{},{},{},{},",
            date.as_str()?,
            name.as_str()?.replace(',', " "),
            category.as_str()?,
            target.as_str()?,
            unilateral,
            log_type.as_i64()?,
        )
        .ok()?;
        for (index, set) in sets.as_array()?.iter().enumerate() {
            let [weight, reps, right_weight, right_reps] = set.as_array()?.as_slice() else {
                return None;
            };
            let (weight, reps) = (weight.as_str()?, reps.as_str()?);
            if index > 0 {
                output.push_str(" | ");
            }
            if unilateral {
                write!(
                    output,
                    "L: {weight}x{reps} / R: {}x{}",
                    right_weight.as_str().unwrap_or_default(),
                    right_reps.as_str().unwrap_or_default(),
                )
                .ok()?;
            } else {
                write!(output, "{weight}x{reps}").ok()?;
            }
        }
        output.push('\n');
    }

    Some(output)
}

#[cfg(test)]
mod tests {
    use super::encode;

    #[test]
    fn matches_legacy_csv_for_unicode_commas_and_bilateral_sets() {
        let input = r#"[["2026-11-15","卧推,热身","STRENGTH","3x8",false,0,[["40","8",null,null],["50","6",null,null]]],["2026-11-15","左右划船","STRENGTH","",true,0,[["20","7","18","8"],["22","5",null,null]]],["2026-11-16","跑步","CARDIO","25m",false,1,[]]]"#;
        assert_eq!(
            encode(input).unwrap(),
            concat!(
                "Date,Name,Category,Target,IsUnilateral,LogType,Sets_Detail\n",
                "2026-11-15,卧推 热身,STRENGTH,3x8,false,0,40x8 | 50x6\n",
                "2026-11-15,左右划船,STRENGTH,,true,0,L: 20x7 / R: 18x8 | L: 22x5 / R: x\n",
                "2026-11-16,跑步,CARDIO,25m,false,1,\n"
            )
        );
        assert!(encode("[[1]]").is_none());
    }
}
